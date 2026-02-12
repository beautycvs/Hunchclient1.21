/*
 * Decompiled with CFR 0.152.
 */
package dev.hunchclient.module;

import dev.hunchclient.HunchClient;
import dev.hunchclient.module.Module;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {
    private static ModuleManager instance;
    private final List<Module> modules = new ArrayList<Module>();

    private ModuleManager() {
        HunchClient.LOGGER.info("ModuleManager initialized");
    }

    public static ModuleManager getInstance() {
        if (instance == null) {
            instance = new ModuleManager();
        }
        return instance;
    }

    public void registerModule(Module module) {
        this.modules.add(module);
        HunchClient.LOGGER.info("Registered module: {} (Category: {}, Safe: {})", new Object[]{module.getName(), module.getCategory(), module.isWatchdogSafe()});
    }

    public List<Module> getModules() {
        return new ArrayList<Module>(this.modules);
    }

    public List<Module> getModulesByCategory(Module.Category category) {
        return this.modules.stream().filter(module -> module.getCategory() == category).collect(Collectors.toList());
    }

    public Module getModuleByName(String name) {
        return this.modules.stream().filter(module -> module.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public <T extends Module> T getModule(Class<T> moduleClass) {
        return (T)((Module)this.modules.stream().filter(module -> moduleClass.isInstance(module)).findFirst().orElse(null));
    }

    public void tick() {
        for (Module module : this.modules) {
            if (!module.isEnabled()) continue;
            try {
                module.onTick();
            }
            catch (Exception e) {
                HunchClient.LOGGER.error("Error ticking module {}: {}", (Object)module.getName(), (Object)e.getMessage());
            }
        }
    }

    public List<Module> getEnabledModules() {
        return this.modules.stream().filter(Module::isEnabled).collect(Collectors.toList());
    }

    public int getModuleCount(Module.Category category) {
        return (int)this.modules.stream().filter(m -> m.getCategory() == category).count();
    }
}

