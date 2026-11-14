use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine;
use jni::objects::{JClass, JLongArray, JObject, JObjectArray, JString};
use jni::sys::{jint, jlong, jintArray, jlongArray, jobjectArray, jstring};
use std::collections::{HashMap, HashSet};
use jni::JNIEnv;

/// Mirrors ContactSelectionOperations.toggle without changing ordering or limits.
fn toggle_selection(selected_ids: &[i64], contact_id: i64, max_tiles: i32) -> Option<Vec<i64>> {
    let mut updated = selected_ids.to_vec();

    if let Some(index) = updated.iter().position(|id| *id == contact_id) {
        updated.remove(index);
        return Some(updated);
    }

    if max_tiles < 0 || updated.len() >= max_tiles as usize {
        return None;
    }

    updated.push(contact_id);
    Some(updated)
}

/// Mirrors ContactSelectionOperations.move, including first-match behavior.
fn move_selection(selected_ids: &[i64], contact_id: i64, direction: i32) -> Option<Vec<i64>> {
    if direction != -1 && direction != 1 {
        return None;
    }

    let from_index = selected_ids.iter().position(|id| *id == contact_id)?;
    let to_index = from_index as i64 + direction as i64;
    if to_index < 0 || to_index >= selected_ids.len() as i64 {
        return None;
    }

    let mut updated = selected_ids.to_vec();
    updated.swap(from_index, to_index as usize);
    Some(updated)
}

fn read_long_array(env: &mut JNIEnv, array: &JLongArray) -> Result<Vec<i64>, jni::errors::Error> {
    let length = env.get_array_length(array)?;
    let mut values = vec![0_i64; length as usize];
    env.get_long_array_region(array, 0, &mut values)?;
    Ok(values)
}

fn write_long_array(env: &mut JNIEnv, values: Option<Vec<i64>>) -> jlongArray {
    let Some(values) = values else {
        return std::ptr::null_mut();
    };

    let array = match env.new_long_array(values.len() as jint) {
        Ok(array) => array,
        Err(_) => return std::ptr::null_mut(),
    };

    if env.set_long_array_region(&array, 0, &values).is_err() {
        return std::ptr::null_mut();
    }

    array.into_raw()
}

fn read_int_array(env: &mut JNIEnv, array: &jni::objects::JIntArray) -> Result<Vec<i32>, jni::errors::Error> {
    let length = env.get_array_length(array)?;
    let mut values = vec![0_i32; length as usize];
    env.get_int_array_region(array, 0, &mut values)?;
    Ok(values)
}

fn read_string_array(env: &mut JNIEnv, array: &JObjectArray) -> Result<Vec<String>, jni::errors::Error> {
    let length = env.get_array_length(array)?;
    let mut values = Vec::with_capacity(length as usize);
    for index in 0..length {
        let object = env.get_object_array_element(array, index)?;
        let value: String = env.get_string(&JString::from(object))?.into();
        values.push(value);
    }
    Ok(values)
}

fn write_string_array(env: &mut JNIEnv, values: &[String]) -> jobjectArray {
    let array = match env.new_object_array(values.len() as jint, "java/lang/String", JObject::null()) {
        Ok(array) => array,
        Err(_) => return std::ptr::null_mut(),
    };
    for (index, value) in values.iter().enumerate() {
        let string = match env.new_string(value) {
            Ok(string) => string,
            Err(_) => return std::ptr::null_mut(),
        };
        if env.set_object_array_element(&array, index as jint, string).is_err() {
            return std::ptr::null_mut();
        }
    }
    array.into_raw()
}

fn resolve_lookup_ids(
    lookup_keys: &[String],
    contact_ids: &[i64],
    contact_lookup_keys: &[String],
) -> Vec<i64> {
    let mut by_lookup_key = HashMap::new();
    for (id, key) in contact_ids.iter().zip(contact_lookup_keys.iter()) {
        by_lookup_key.insert(key.as_str(), *id);
    }

    let mut seen = HashSet::new();
    lookup_keys
        .iter()
        .filter_map(|key| by_lookup_key.get(key.as_str()).copied())
        .filter(|id| seen.insert(*id))
        .collect()
}

fn lookup_keys_for_ids(ids: &[i64], contact_ids: &[i64], contact_lookup_keys: &[String]) -> Vec<String> {
    let mut by_id = HashMap::new();
    for (id, key) in contact_ids.iter().zip(contact_lookup_keys.iter()) {
        by_id.insert(*id, key.as_str());
    }

    ids.iter()
        .map(|id| by_id.get(id).map(|key| (*key).to_owned()).unwrap_or_default())
        .collect()
}


fn write_int_array(env: &mut JNIEnv, values: &[i32]) -> jintArray {
    let array = match env.new_int_array(values.len() as jint) {
        Ok(array) => array,
        Err(_) => return std::ptr::null_mut(),
    };

    if env.set_int_array_region(&array, 0, values).is_err() {
        return std::ptr::null_mut();
    }

    array.into_raw()
}

/// Returns contact indices in display order while preserving Kotlin map/list semantics.
fn build_contact_display_order(
    all_ids: &[i64],
    selected_ids: &[i64],
    query_matches: &[bool],
    query_is_blank: bool,
) -> Vec<i32> {
    let mut selected_by_id = HashMap::new();
    for (index, id) in all_ids.iter().enumerate() {
        selected_by_id.insert(*id, index);
    }

    let selected_set: HashSet<i64> = selected_ids.iter().copied().collect();
    let mut result = Vec::new();

    for id in selected_ids {
        if let Some(index) = selected_by_id.get(id) {
            result.push(*index as i32);
        }
    }

    for (index, id) in all_ids.iter().enumerate() {
        if selected_set.contains(id) {
            continue;
        }
        if query_is_blank || query_matches.get(index).copied().unwrap_or(false) {
            result.push(index as i32);
        }
    }

    result
}

#[no_mangle]
pub extern "system" fn Java_com_toxictrace_nexusconnect_util_NativePhoneNumber_normalize(
    mut env: JNIEnv,
    _class: JClass,
    number: JString,
) -> jstring {
    let input: String = match env.get_string(&number) {
        Ok(value) => value.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    match env.new_string(normalize_number(&input)) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Rust implementation of the previous Kotlin selection toggle operation.
#[no_mangle]
pub extern "system" fn Java_com_toxictrace_nexusconnect_viewmodel_ContactSelectionOperations_toggleNative(
    mut env: JNIEnv,
    _class: JClass,
    selected_ids: JLongArray,
    contact_id: jlong,
    max_tiles: jint,
) -> jlongArray {
    let selected = match read_long_array(&mut env, &selected_ids) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };

    write_long_array(&mut env, toggle_selection(&selected, contact_id, max_tiles))
}

/// Rust implementation of the previous Kotlin selection move operation.
#[no_mangle]
pub extern "system" fn Java_com_toxictrace_nexusconnect_viewmodel_ContactSelectionOperations_moveNative(
    mut env: JNIEnv,
    _class: JClass,
    selected_ids: JLongArray,
    contact_id: jlong,
    direction: jint,
) -> jlongArray {
    let selected = match read_long_array(&mut env, &selected_ids) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };

    write_long_array(&mut env, move_selection(&selected, contact_id, direction))
}

/// Mirrors SelectionLimit.trim while preserving order and capacity edge cases.
fn trim_selection(ids: &[i64], capacity: i32) -> Vec<i64> {
    ids.iter().take(capacity.max(0) as usize).copied().collect()
}

#[no_mangle]
pub extern "system" fn Java_com_toxictrace_nexusconnect_viewmodel_SelectionLimit_trimNative(
    mut env: JNIEnv,
    _class: JClass,
    selected_ids: JLongArray,
    capacity: jint,
) -> jlongArray {
    let selected = match read_long_array(&mut env, &selected_ids) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };

    write_long_array(&mut env, Some(trim_selection(&selected, capacity)))
}



/// Calculates photo-cache retention, reload IDs, and contact indices in display order.
fn build_photo_cache_plan(
    contact_ids: &[i64],
    photo_uris: &[String],
    has_photo: &[bool],
    cached_bitmap_ids: &[i64],
    cached_photo_ids: &[i64],
    cached_photo_uris: &[String],
) -> Vec<i64> {
    let mut cached_uri_by_id = HashMap::new();
    for (id, uri) in cached_photo_ids.iter().zip(cached_photo_uris.iter()) {
        cached_uri_by_id.insert(*id, uri.as_str());
    }

    let cached_bitmaps: HashSet<i64> = cached_bitmap_ids.iter().copied().collect();
    let mut retained_ids = HashSet::new();
    let mut reload_ids = HashSet::new();

    for (index, id) in contact_ids.iter().enumerate() {
        if !has_photo.get(index).copied().unwrap_or(false) {
            continue;
        }
        let uri = match photo_uris.get(index) {
            Some(uri) => uri,
            None => continue,
        };
        retained_ids.insert(*id);
        if !cached_bitmaps.contains(id) || cached_uri_by_id.get(id).copied() != Some(uri.as_str()) {
            reload_ids.insert(*id);
        }
    }

    let contacts_to_load: Vec<i64> = contact_ids
        .iter()
        .enumerate()
        .filter_map(|(index, id)| {
            if has_photo.get(index).copied().unwrap_or(false) && reload_ids.contains(id) {
                Some(index as i64)
            } else {
                None
            }
        })
        .collect();

    let mut result = Vec::with_capacity(3 + retained_ids.len() + reload_ids.len() + contacts_to_load.len());
    result.push(retained_ids.len() as i64);
    result.push(reload_ids.len() as i64);
    result.push(contacts_to_load.len() as i64);
    result.extend(retained_ids);
    result.extend(reload_ids);
    result.extend(contacts_to_load);
    result
}

#[no_mangle]
pub extern "system" fn Java_com_toxictrace_nexusconnect_viewmodel_ContactDisplayList_buildOrderNative(
    mut env: JNIEnv,
    _class: JClass,
    all_ids: JLongArray,
    selected_ids: JLongArray,
    query_matches: JLongArray,
    query_is_blank: jint,
) -> jintArray {
    let all = match read_long_array(&mut env, &all_ids) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let selected = match read_long_array(&mut env, &selected_ids) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let matches = match read_long_array(&mut env, &query_matches) {
        Ok(values) => values.into_iter().map(|value| value != 0).collect::<Vec<_>>(),
        Err(_) => return std::ptr::null_mut(),
    };

    let order = build_contact_display_order(&all, &selected, &matches, query_is_blank != 0);
    write_int_array(&mut env, &order)
}


#[no_mangle]
pub extern "system" fn Java_com_toxictrace_nexusconnect_viewmodel_PhotoCachePlan_buildNative(
    mut env: JNIEnv,
    _class: JClass,
    contact_ids: JLongArray,
    photo_uris: JObjectArray,
    has_photo: JLongArray,
    cached_bitmap_ids: JLongArray,
    cached_photo_ids: JLongArray,
    cached_photo_uris: JObjectArray,
) -> jlongArray {
    let ids = match read_long_array(&mut env, &contact_ids) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let uris = match read_string_array(&mut env, &photo_uris) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let photo_flags = match read_long_array(&mut env, &has_photo) {
        Ok(values) => values.into_iter().map(|value| value != 0).collect::<Vec<_>>(),
        Err(_) => return std::ptr::null_mut(),
    };
    let bitmap_ids = match read_long_array(&mut env, &cached_bitmap_ids) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let cached_ids = match read_long_array(&mut env, &cached_photo_ids) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let cached_uris = match read_string_array(&mut env, &cached_photo_uris) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };

    let result = build_photo_cache_plan(
        &ids,
        &uris,
        &photo_flags,
        &bitmap_ids,
        &cached_ids,
        &cached_uris,
    );
    write_long_array(&mut env, Some(result))
}

#[no_mangle]
pub extern "system" fn Java_com_toxictrace_nexusconnect_viewmodel_ContactLookupResolver_resolveIdsNative(
    mut env: JNIEnv,
    _class: JClass,
    lookup_keys: JObjectArray,
    contact_ids: JLongArray,
    contact_lookup_keys: JObjectArray,
) -> jlongArray {
    let keys = match read_string_array(&mut env, &lookup_keys) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let ids = match read_long_array(&mut env, &contact_ids) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let contact_keys = match read_string_array(&mut env, &contact_lookup_keys) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    write_long_array(&mut env, Some(resolve_lookup_ids(&keys, &ids, &contact_keys)))
}

#[no_mangle]
pub extern "system" fn Java_com_toxictrace_nexusconnect_viewmodel_ContactLookupResolver_lookupKeysForIdsNative(
    mut env: JNIEnv,
    _class: JClass,
    ids: JLongArray,
    contact_ids: JLongArray,
    contact_lookup_keys: JObjectArray,
) -> jobjectArray {
    let selected_ids = match read_long_array(&mut env, &ids) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let all_ids = match read_long_array(&mut env, &contact_ids) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let keys = match read_string_array(&mut env, &contact_lookup_keys) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let resolved = lookup_keys_for_ids(&selected_ids, &all_ids, &keys);
    write_string_array(&mut env, &resolved)
}

/// Mirrors the previous Kotlin expression:
/// number.replace(Regex("[\\s\\-().+]"), "").takeLast(7)
fn normalize_number(number: &str) -> String {
    let filtered: String = number
        .chars()
        .filter(|character| {
            !matches!(
                character,
                ' ' | '\t' | '\n' | '\u{000B}' | '\u{000C}' | '\r'
                    | '-' | '(' | ')' | '.' | '+'
            )
        })
        .collect();

    filtered.chars().rev().take(7).collect::<String>().chars().rev().collect()
}


/// Orders contact IDs by incoming/outgoing call count, preserving first-seen order for ties.
fn frequent_contact_order(contact_ids: &[i64], call_types: &[i32]) -> Vec<i64> {
    let mut positions = HashMap::new();
    let mut counts: Vec<(i64, i32)> = Vec::new();

    for (contact_id, call_type) in contact_ids.iter().zip(call_types.iter()) {
        if *call_type != 1 && *call_type != 2 {
            continue;
        }
        let index = match positions.get(contact_id) {
            Some(index) => *index,
            None => {
                let index = counts.len();
                positions.insert(*contact_id, index);
                counts.push((*contact_id, 0));
                index
            }
        };
        counts[index].1 = counts[index].1.wrapping_add(1);
    }

    counts.sort_by(|left, right| right.1.cmp(&left.1));
    counts.into_iter().map(|(contact_id, _)| contact_id).collect()
}

#[no_mangle]
pub extern "system" fn Java_com_toxictrace_nexusconnect_data_repository_FrequentContactOrder_orderNative(
    mut env: JNIEnv,
    _class: JClass,
    contact_ids: JLongArray,
    call_types: jni::objects::JIntArray,
) -> jlongArray {
    let ids = match read_long_array(&mut env, &contact_ids) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let types = match read_int_array(&mut env, &call_types) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };

    write_long_array(&mut env, Some(frequent_contact_order(&ids, &types)))
}


/// Builds normalized numbers for widget tiles while preserving map and tile order.
fn extract_tile_norms(
    number_keys: &[String],
    number_ids: &[i64],
    tile_ids: &[i64],
    tile_phone_numbers: &[String],
) -> Vec<String> {
    let tile_id_set: HashSet<i64> = tile_ids.iter().copied().collect();
    let mut norms = Vec::new();

    for (key, id) in number_keys.iter().zip(number_ids.iter()) {
        if tile_id_set.contains(id) {
            norms.push(key.clone());
        }
    }

    for (id, phone_number) in tile_ids.iter().zip(tile_phone_numbers.iter()) {
        if *id < 0 {
            let norm = normalize_number(phone_number);
            if !norm.trim().is_empty() {
                norms.push(norm);
            }
        }
    }

    norms
}

#[no_mangle]
pub extern "system" fn Java_com_toxictrace_nexusconnect_widget_WidgetTileNorms_extractNative(
    mut env: JNIEnv,
    _class: JClass,
    number_keys: JObjectArray,
    number_ids: JLongArray,
    tile_ids: JLongArray,
    tile_phone_numbers: JObjectArray,
) -> jobjectArray {
    let keys = match read_string_array(&mut env, &number_keys) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let ids = match read_long_array(&mut env, &number_ids) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let contact_ids = match read_long_array(&mut env, &tile_ids) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let phones = match read_string_array(&mut env, &tile_phone_numbers) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };

    write_string_array(&mut env, &extract_tile_norms(&keys, &ids, &contact_ids, &phones))
}


/// Returns indices of the first recent call for each unknown normalized number.
fn unknown_recent_call_indices(
    norms: &[String],
    dates: &[i64],
    types: &[i32],
    non_blank: &[i32],
    known_norms: &[String],
    row_count: usize,
    limit: i32,
) -> Vec<i32> {
    if limit <= 0 {
        return Vec::new();
    }
    let known: HashSet<&str> = known_norms.iter().map(String::as_str).collect();
    let mut seen = HashSet::new();
    let mut result = Vec::new();
    let count = row_count.min(norms.len()).min(dates.len()).min(types.len()).min(non_blank.len());
    for index in 0..count {
        let norm = norms[index].as_str();
        if non_blank[index] != 0 && !known.contains(norm) && seen.insert(norm) {
            result.push(index as i32);
            if result.len() >= limit as usize {
                break;
            }
        }
    }
    result
}

#[no_mangle]
pub extern "system" fn Java_com_toxictrace_nexusconnect_data_repository_UnknownRecentCallFilter_filterNative(
    mut env: JNIEnv,
    _class: JClass,
    numbers: JObjectArray,
    norms: JObjectArray,
    dates: JLongArray,
    types: jni::objects::JIntArray,
    non_blank: jni::objects::JIntArray,
    known_norms: JObjectArray,
    limit: jint,
) -> jintArray {
    let number_values = match read_string_array(&mut env, &numbers) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let normalized = match read_string_array(&mut env, &norms) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let date_values = match read_long_array(&mut env, &dates) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let type_values = match read_int_array(&mut env, &types) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let non_blank_values = match read_int_array(&mut env, &non_blank) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };
    let known_values = match read_string_array(&mut env, &known_norms) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };

    let indices = unknown_recent_call_indices(
        &normalized,
        &date_values,
        &type_values,
        &non_blank_values,
        &known_values,
        number_values.len(),
        limit,
    );
    write_int_array(&mut env, &indices)
}

/// Serializes selected contact IDs using the existing comma-separated storage format.
fn serialize_contact_ids(ids: &[i64]) -> String {
    ids.iter().map(|id| id.to_string()).collect::<Vec<_>>().join(",")
}

 
/// Serializes lookup keys using URL-safe Base64 without padding.
fn serialize_lookup_keys(keys: &[String]) -> String {
    keys.iter()
        .map(|key| URL_SAFE_NO_PAD.encode(key.as_bytes()))
        .collect::<Vec<_>>()
        .join(",")
}

#[no_mangle]
pub extern "system" fn Java_com_toxictrace_nexusconnect_data_preferences_NativeSelectedContactLookupKeys_serializeNative(
    mut env: JNIEnv,
    _class: JClass,
    keys: JObjectArray,
) -> jstring {
    let values = match read_string_array(&mut env, &keys) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };

    match env.new_string(serialize_lookup_keys(&values)) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_toxictrace_nexusconnect_data_preferences_NativeSelectedContactIds_serializeNative(
    mut env: JNIEnv,
    _class: JClass,
    ids: JLongArray,
) -> jstring {
    let values = match read_long_array(&mut env, &ids) {
        Ok(values) => values,
        Err(_) => return std::ptr::null_mut(),
    };

    match env.new_string(serialize_contact_ids(&values)) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[cfg(test)]
mod tests {
    use super::{build_contact_display_order, build_photo_cache_plan, extract_tile_norms, frequent_contact_order, lookup_keys_for_ids, move_selection, normalize_number, resolve_lookup_ids, serialize_contact_ids, serialize_lookup_keys, toggle_selection, trim_selection, unknown_recent_call_indices};

    #[test]
    fn frequent_contact_order_sorts_by_call_count() {
        assert_eq!(
            frequent_contact_order(&[10, 20, 10, 30, 10, 20], &[1, 1, 2, 2, 1, 2]),
            vec![10, 20, 30]
        );
    }

    #[test]
    fn frequent_contact_order_preserves_first_seen_order_for_ties() {
        assert_eq!(
            frequent_contact_order(&[30, 20, 10, 20, 30, 10], &[1, 1, 1, 2, 2, 2]),
            vec![30, 20, 10]
        );
    }

    #[test]
    fn frequent_contact_order_ignores_missed_and_unmatched_calls() {
        assert_eq!(
            frequent_contact_order(&[10, 20, 10, 30], &[3, 1, 2, 5]),
            vec![20, 10]
        );
    }

    #[test]
    fn frequent_contact_order_handles_empty_and_mismatched_inputs() {
        assert_eq!(frequent_contact_order(&[], &[]), Vec::<i64>::new());
        assert_eq!(frequent_contact_order(&[7, 8], &[1]), vec![7]);
        assert_eq!(frequent_contact_order(&[7], &[1, 2]), vec![7]);
    }

    #[test]
    fn toggle_adds_contact_at_end() {
        assert_eq!(toggle_selection(&[1, 2], 3, 4), Some(vec![1, 2, 3]));
    }

    #[test]
    fn toggle_removes_selected_contact_without_changing_other_order() {
        assert_eq!(toggle_selection(&[1, 2, 3], 2, 3), Some(vec![1, 3]));
    }

    #[test]
    fn toggle_rejects_addition_when_at_capacity() {
        assert_eq!(toggle_selection(&[1, 2], 3, 2), None);
    }

    #[test]
    fn toggle_allows_removal_when_at_capacity() {
        assert_eq!(toggle_selection(&[1, 2], 2, 2), Some(vec![1]));
    }

    #[test]
    fn toggle_rejects_addition_for_zero_or_negative_capacity() {
        assert_eq!(toggle_selection(&[], 1, 0), None);
        assert_eq!(toggle_selection(&[], 1, -1), None);
    }

    #[test]
    fn toggle_removes_only_the_first_duplicate() {
        assert_eq!(toggle_selection(&[1, 2, 1], 1, 3), Some(vec![2, 1]));
    }

    #[test]
    fn move_swaps_contact_up_one_position() {
        assert_eq!(move_selection(&[1, 2, 3], 2, -1), Some(vec![2, 1, 3]));
    }

    #[test]
    fn move_swaps_contact_down_one_position() {
        assert_eq!(move_selection(&[1, 2, 3], 2, 1), Some(vec![1, 3, 2]));
    }

    #[test]
    fn move_rejects_invalid_directions() {
        assert_eq!(move_selection(&[1, 2], 1, 0), None);
        assert_eq!(move_selection(&[1, 2], 1, 2), None);
    }

    #[test]
    fn move_rejects_missing_ids_and_boundary_moves() {
        assert_eq!(move_selection(&[1, 2], 9, -1), None);
        assert_eq!(move_selection(&[1, 2], 1, -1), None);
        assert_eq!(move_selection(&[1, 2], 2, 1), None);
    }

    #[test]
    fn move_rejects_empty_selection() {
        assert_eq!(move_selection(&[], 1, 1), None);
    }

    #[test]
    fn move_uses_first_matching_duplicate() {
        assert_eq!(move_selection(&[1, 2, 1], 1, 1), Some(vec![2, 1, 1]));
    }

    #[test]
    fn trim_selection_keeps_all_ids_within_capacity() {
        assert_eq!(trim_selection(&[4, 8], 3), vec![4, 8]);
    }

    #[test]
    fn trim_selection_limits_ids_and_preserves_order() {
        assert_eq!(trim_selection(&[4, 8, 2], 2), vec![4, 8]);
    }

    #[test]
    fn trim_selection_returns_empty_for_zero_or_negative_capacity() {
        assert_eq!(trim_selection(&[4, 8], 0), Vec::<i64>::new());
        assert_eq!(trim_selection(&[4, 8], -1), Vec::<i64>::new());
    }

    #[test]
    fn trim_selection_keeps_empty_selection_empty() {
        assert_eq!(trim_selection(&[], 12), Vec::<i64>::new());
    }




    #[test]
    fn photo_cache_plan_reuses_unchanged_cached_photos() {
        let result = build_photo_cache_plan(
            &[1, 2],
            &["uri-1".into(), "uri-2".into()],
            &[true, true],
            &[1, 2],
            &[1, 2],
            &["uri-1".into(), "uri-2".into()],
        );
        assert_eq!(result[0..3], [2, 0, 0]);
        assert_eq!(result.len(), 5);
    }

    #[test]
    fn photo_cache_plan_reloads_missing_bitmaps_and_changed_uris() {
        let result = build_photo_cache_plan(
            &[1, 2],
            &["new-uri".into(), "uri-2".into()],
            &[true, true],
            &[2],
            &[1, 2],
            &["old-uri".into(), "uri-2".into()],
        );
        assert_eq!(result[0..3], [2, 1, 1]);
        assert!(result[3..5].contains(&1));
        assert!(result[3..5].contains(&2));
        assert_eq!(result[6], 0);
    }

    #[test]
    fn photo_cache_plan_excludes_blank_and_missing_photos_and_deleted_ids() {
        let result = build_photo_cache_plan(
            &[1, 2, 3],
            &["uri-1".into(), "   ".into(), "".into()],
            &[true, false, false],
            &[1, 2, 3, 4],
            &[1, 2, 3, 4],
            &["uri-1".into(), "uri-2".into(), "uri-3".into(), "uri-4".into()],
        );
        assert_eq!(result[0..3], [1, 0, 0]);
        assert_eq!(result[3], 1);
        assert_eq!(result.len(), 4);
    }

    #[test]
    fn photo_cache_plan_preserves_contact_load_order_and_duplicate_id_semantics() {
        let result = build_photo_cache_plan(
            &[7, 7, 8],
            &["old-uri".into(), "new-uri".into(), "uri-8".into()],
            &[true, true, true],
            &[7, 8],
            &[7, 8],
            &["cached-uri".into(), "uri-8".into()],
        );
        assert_eq!(result[0..3], [2, 1, 2]);
        assert!(result[3..5].contains(&7));
        assert!(result[3..5].contains(&8));
        assert_eq!(result[5], 7);
        assert_eq!(result[6..8], [0, 1]);
    }

    #[test]
    fn photo_cache_plan_handles_mismatched_input_lengths_without_panicking() {
        let result = build_photo_cache_plan(
            &[1, 2],
            &["uri-1".into()],
            &[true, true],
            &[],
            &[],
            &[],
        );
        assert_eq!(result[0..3], [1, 1, 1]);
        assert_eq!(result[3], 1);
        assert_eq!(result[4], 1);
        assert_eq!(result[5], 0);
    }

    #[test]
    fn contact_display_order_keeps_selected_ids_in_saved_order() {
        assert_eq!(
            build_contact_display_order(&[1, 2, 3, 4], &[3, 1], &[true; 4], true),
            vec![2, 0, 1, 3]
        );
    }

    #[test]
    fn contact_display_order_filters_only_unselected_contacts() {
        assert_eq!(
            build_contact_display_order(&[1, 2, 3, 4], &[3], &[false, true, false, false], false),
            vec![2, 1]
        );
    }

    #[test]
    fn contact_display_order_skips_missing_selected_ids() {
        assert_eq!(
            build_contact_display_order(&[1, 2, 3, 4], &[99, 2], &[true; 4], true),
            vec![1, 0, 2, 3]
        );
    }

    #[test]
    fn contact_display_order_uses_last_contact_for_duplicate_ids() {
        assert_eq!(
            build_contact_display_order(&[1, 2, 1, 3], &[1], &[true; 4], true),
            vec![2, 1, 3]
        );
    }

    #[test]
    fn contact_display_order_preserves_repeated_selected_ids() {
        assert_eq!(
            build_contact_display_order(&[1, 2], &[2, 2], &[true; 2], true),
            vec![1, 1, 0]
        );
    }

    #[test]
    fn resolve_lookup_ids_preserves_saved_order_and_skips_missing_keys() {
        let keys = vec!["key-b".to_string(), "missing".to_string(), "key-a".to_string()];
        let ids = vec![4553, 7001];
        let contact_keys = vec!["key-a".to_string(), "key-b".to_string()];
        assert_eq!(resolve_lookup_ids(&keys, &ids, &contact_keys), vec![7001, 4553]);
    }

    #[test]
    fn resolve_lookup_ids_uses_last_contact_for_duplicate_lookup_key() {
        let keys = vec!["same".to_string()];
        let ids = vec![10, 20];
        let contact_keys = vec!["same".to_string(), "same".to_string()];
        assert_eq!(resolve_lookup_ids(&keys, &ids, &contact_keys), vec![20]);
    }

    #[test]
    fn resolve_lookup_ids_removes_duplicate_resolved_ids() {
        let keys = vec!["first".to_string(), "second".to_string()];
        let ids = vec![42, 42];
        let contact_keys = vec!["first".to_string(), "second".to_string()];
        assert_eq!(resolve_lookup_ids(&keys, &ids, &contact_keys), vec![42]);
    }

    #[test]
    fn lookup_keys_for_ids_preserves_selected_order_and_missing_placeholders() {
        let ids = vec![7001, 9999, 4553];
        let contact_ids = vec![4553, 7001];
        let keys = vec!["key-a".to_string(), "key-b".to_string()];
        assert_eq!(
            lookup_keys_for_ids(&ids, &contact_ids, &keys),
            vec!["key-b".to_string(), "".to_string(), "key-a".to_string()]
        );
    }

    #[test]
    fn lookup_keys_for_ids_uses_last_contact_for_duplicate_id() {
        let ids = vec![7];
        let contact_ids = vec![7, 7];
        let keys = vec!["old-key".to_string(), "new-key".to_string()];
        assert_eq!(lookup_keys_for_ids(&ids, &contact_ids, &keys), vec!["new-key".to_string()]);
    }

    #[test]
    fn normalize_removes_the_same_ascii_formatting_characters() {
        assert_eq!(normalize_number("+1 (234) 567-8901"), "5678901");
    }

    #[test]
    fn normalize_keeps_only_the_last_seven_remaining_characters() {
        assert_eq!(normalize_number("12.34-56(78)+90"), "4567890");
    }

    #[test]
    fn normalize_preserves_short_and_empty_values() {
        assert_eq!(normalize_number(" 123 "), "123");
        assert_eq!(normalize_number(""), "");
    }

    #[test]
    fn normalize_removes_java_regex_ascii_whitespace() {
        assert_eq!(normalize_number("\t12\n34\u{000B}56\u{000C}78\r90"), "4567890");
    }

    #[test]
    fn widget_tile_norms_preserve_map_order_and_include_only_tile_contacts() {
        assert_eq!(
            extract_tile_norms(
                &["norm-a".into(), "norm-b".into(), "norm-c".into()],
                &[10, 20, 30],
                &[30, 10, -1],
                &["".into(), "".into(), "+1 (234) 567-8901".into()],
            ),
            vec!["norm-a", "norm-c", "5678901"]
        );
    }

    #[test]
    fn widget_tile_norms_preserve_duplicate_keys_and_unknown_tile_order() {
        assert_eq!(
            extract_tile_norms(
                &["same".into(), "same".into()],
                &[7, 7],
                &[7, -2, -3, 8],
                &["".into(), "12-34-567".into(), "   ".into(), "999".into()],
            ),
            vec!["same", "same", "1234567"]
        );
    }

    #[test]
    fn widget_tile_norms_handle_empty_and_mismatched_inputs() {
        assert_eq!(extract_tile_norms(&[], &[], &[], &[]), Vec::<String>::new());
        assert_eq!(
            extract_tile_norms(&["keep".into(), "ignored".into()], &[1], &[1], &[]),
            vec!["keep"]
        );
    }


    #[test]
    fn unknown_recent_calls_exclude_known_blank_and_duplicate_numbers() {
        let norms = vec!["known".into(), "unknown-a".into(), "unknown-a".into(), "unknown-b".into(), "blank".into()];
        assert_eq!(
            unknown_recent_call_indices(&norms, &[5, 4, 3, 2, 1], &[1, 2, 3, 4, 5], &[1, 1, 1, 1, 0], &["known".into()], 5, 20),
            vec![1, 3]
        );
    }

    #[test]
    fn unknown_recent_calls_respect_limit_and_input_order() {
        let norms = vec!["a".into(), "b".into(), "c".into()];
        assert_eq!(
            unknown_recent_call_indices(&norms, &[3, 2, 1], &[1, 2, 3], &[1, 1, 1], &[], 3, 2),
            vec![0, 1]
        );
    }

    #[test]
    fn unknown_recent_calls_handle_non_positive_limits_and_mismatched_arrays() {
        let norms = vec!["a".into(), "b".into(), "c".into()];
        assert_eq!(
            unknown_recent_call_indices(&norms, &[3, 2], &[1, 2, 3], &[1, 1, 1], &[], 3, 10),
            vec![0, 1]
        );
        assert!(unknown_recent_call_indices(&norms, &[3, 2, 1], &[1, 2, 3], &[1, 1, 1], &[], 3, 0).is_empty());
        assert_eq!(
            unknown_recent_call_indices(&norms, &[3, 2, 1], &[1, 2, 3], &[1, 1, 1], &[], 2, 10),
            vec![0, 1]
        );
    }

    #[test]
    fn selected_contact_ids_serialization_preserves_order_and_duplicates() {
        assert_eq!(serialize_contact_ids(&[42, 7, 42]), "42,7,42");
    }

    #[test]
    fn selected_contact_ids_serialization_handles_empty_and_negative_values() {
        assert_eq!(serialize_contact_ids(&[]), "");
        assert_eq!(serialize_contact_ids(&[-7, 0, 15]), "-7,0,15");
    }

    #[test]
    fn selected_contact_ids_serialization_handles_long_boundaries() {
        assert_eq!(
            serialize_contact_ids(&[i64::MIN, i64::MAX]),
            format!("{},{}", i64::MIN, i64::MAX)
        );
    }

    #[test]
    fn normalize_preserves_non_ascii_digits() {
        assert_eq!(normalize_number("+１２３-４５６７８"), "２３４５６７８");
    }
    #[test]
    fn selected_lookup_keys_serialization_preserves_url_safe_base64_format() {
        let keys = vec![
            "0r7-abc:person,one".to_string(),
            "lookup/key_two".to_string(),
            "another-key".to_string(),
            "ключ".to_string(),
        ];
        assert_eq!(
            serialize_lookup_keys(&keys),
            "MHI3LWFiYzpwZXJzb24sb25l,bG9va3VwL2tleV90d28,YW5vdGhlci1rZXk,0LrQu9GO0Yc"
        );
    }

    #[test]
    fn selected_lookup_keys_serialization_returns_empty_for_empty_input() {
        assert_eq!(serialize_lookup_keys(&[]), "");
    }

}
