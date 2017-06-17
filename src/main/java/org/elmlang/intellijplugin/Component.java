package org.elmlang.intellijplugin;

import com.intellij.AppTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class Component implements ApplicationComponent {

    private static final String COMPONENT_NAME = "Elm Language Component";

    public void initComponent() {
        MessageBus bus = ApplicationManager.getApplication().getMessageBus();
        MessageBusConnection connection = bus.connect();
        connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new ElmLanguageFileDocumentManager());
    }

    public void disposeComponent() {
    }

    @NotNull
    public String getComponentName() {
        return COMPONENT_NAME;
    }

}
