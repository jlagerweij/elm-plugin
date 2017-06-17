package org.elmlang.intellijplugin.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

@State(name = "ElmPluginSettings")
public class ElmPluginSettings implements PersistentStateComponent<ElmPluginSettings> {
    public String elmMakeExecutable = "";
    public boolean pluginEnabled;

    public static ElmPluginSettings getInstance(Project project) {
        return ServiceManager.getService(project, ElmPluginSettings.class);
    }

    @Nullable
    @Override
    public ElmPluginSettings getState() {
        return this;
    }

    @Override
    public void loadState(ElmPluginSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
