package com.budgettracker.util;

import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = AppConfig.class.getResourceAsStream("/config.properties")) {
            if (in != null) PROPS.load(in);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private AppConfig() {}

    public static String getDbUrl()      { return PROPS.getProperty("db.url"); }
    public static String getDbUser()     { return PROPS.getProperty("db.user"); }
    public static String getDbPassword() { return PROPS.getProperty("db.password"); }
}
