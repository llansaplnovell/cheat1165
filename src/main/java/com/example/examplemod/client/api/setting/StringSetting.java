package com.example.examplemod.client.api.setting;

/** Free-form text, rendered as a text field. */
public class StringSetting extends Setting<String> {

    public StringSetting(String name, String description, String defaultValue) {
        super(name, description, defaultValue);
    }

    public StringSetting(String name, String defaultValue) {
        this(name, "", defaultValue);
    }
}
