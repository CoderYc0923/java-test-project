package com.sky.takeout.common.constant;

public final class RegexConstant {

    /** 中国大陆手机号：1 开头共 11 位 */
    public static final String PHONE = "^1\\d{10}$";

    /** 性别：0 女 / 1 男 */
    public static final String SEX = "^[01]$";

    /** 身份证：18 位 */
    public static final String ID_NUMBER = "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$";

    private RegexConstant() {
    }
}
