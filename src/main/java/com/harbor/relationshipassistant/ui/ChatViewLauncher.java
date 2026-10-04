package com.harbor.relationshipassistant.ui;

import javafx.application.Application;

/** 非模块化 classpath 启动入口（不直接继承 Application，避免 JavaFX 运行时入口限制）。 */
public class ChatViewLauncher {

    public static void main(String[] args) {
        Application.launch(ChatViewApplication.class, args);
    }
}
