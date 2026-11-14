use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;

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

#[cfg(test)]
mod tests {
    use super::normalize_number;

    #[test]
    fn removes_the_same_ascii_formatting_characters() {
        assert_eq!(normalize_number("+1 (234) 567-8901"), "5678901");
    }

    #[test]
    fn keeps_only_the_last_seven_remaining_characters() {
        assert_eq!(normalize_number("12.34-56(78)+90"), "4567890");
    }

    #[test]
    fn preserves_short_and_empty_values() {
        assert_eq!(normalize_number(" 123 "), "123");
        assert_eq!(normalize_number(""), "");
    }

    #[test]
    fn removes_java_regex_ascii_whitespace() {
        assert_eq!(normalize_number("\t12\n34\u{000B}56\u{000C}78\r90"), "4567890");
    }

    #[test]
    fn preserves_non_ascii_digits() {
        assert_eq!(normalize_number("+１２３-４５６７８"), "１２３４５６７８");
    }
}
