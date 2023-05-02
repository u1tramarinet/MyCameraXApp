package com.u1tramarinet.mycameraxapp;

public enum AspectRatio {
    RATIO_4_3(4.0 / 3.0, "4:3"),
    RATIO_16_9(16.0 / 9.0, "16:9"),
    RATIO_1_1(1.0, "1:1"),
    ;
    final double value;
    final String screenName;

    AspectRatio(double value, String screenName) {
        this.value = value;
        this.screenName = screenName;
    }

    static AspectRatio next(AspectRatio origin) {
        switch (origin) {
            case RATIO_1_1:
                return RATIO_4_3;
            case RATIO_16_9:
                return RATIO_1_1;
            case RATIO_4_3:
            default:
                return RATIO_16_9;
        }
    }
}
