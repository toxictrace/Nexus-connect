use jni::objects::{JClass, JLongArray, JString};
use jni::sys::{jint, jlong, jlongArray, jstring};
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

#[cfg(test)]
mod tests {
    use super::{move_selection, normalize_number, toggle_selection};

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
    fn normalize_preserves_non_ascii_digits() {
        assert_eq!(normalize_number("+１２３-４５６７８"), "２３４５６７８");
    }
}
