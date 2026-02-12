package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Change module settings
 */
public class SetCommand extends Command {

    @Override
    public String getName() {
        return "set";
    }

    @Override
    public String getDescription() {
        return "Change module settings";
    }

    @Override
    public String getUsage() {
        return "set <module> <setting> <value>";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"setting", "settings"};
    }

    @Override
    public Category getCategory() {
        return Category.MODULE;
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 2) {
            sendUsage();
            return;
        }

        String moduleName = args[0];
        ModuleManager manager = ModuleManager.getInstance();
        Module module = manager.getModuleByName(moduleName);

        if (module == null) {
            sendError("Module not found: " + moduleName);
            return;
        }

        if (!(module instanceof SettingsProvider)) {
            sendError("Module §e" + module.getName() + " §chas no settings");
            return;
        }

        SettingsProvider settingsProvider = (SettingsProvider) module;

        if (args.length == 1) {
            // List all settings for the module
            displayModuleSettings(module, settingsProvider);
            return;
        }

        String settingName = args[1];

        if (args.length == 2) {
            // Display specific setting
            displaySetting(module, settingsProvider, settingName);
            return;
        }

        // Set the value
        String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        setSetting(module, settingsProvider, settingName, value);
    }

    private void displayModuleSettings(Module module, SettingsProvider provider) {
        List<ModuleSetting> settings = provider.getSettings();

        if (settings.isEmpty()) {
            sendMessage("§7Module §e" + module.getName() + " §7has no settings");
            return;
        }

        sendMessage("§d§l=== " + module.getName() + " Settings ===");

        for (ModuleSetting setting : settings) {
            String value = getSettingValueString(setting);
            String type = getSettingType(setting);

            sendMessage("§e" + setting.getName() + " §8[" + type + "] §7= §f" + value);

            if (setting.getDescription() != null && !setting.getDescription().isEmpty()) {
                sendMessage("  §7" + setting.getDescription());
            }
        }
    }

    private void displaySetting(Module module, SettingsProvider provider, String settingName) {
        ModuleSetting setting = findSetting(provider, settingName);

        if (setting == null) {
            sendError("Setting not found: " + settingName);
            listAvailableSettings(provider);
            return;
        }

        sendMessage("§d§l=== " + module.getName() + " :: " + setting.getName() + " ===");
        sendMessage("§7Type: §e" + getSettingType(setting));
        sendMessage("§7Value: §f" + getSettingValueString(setting));

        if (setting.getDescription() != null) {
            sendMessage("§7Description: " + setting.getDescription());
        }

        // Show valid values for specific types
        if (setting instanceof CheckboxSetting) {
            sendMessage("§7Valid values: §etrue, false");
        } else if (setting instanceof SliderSetting slider) {
            sendMessage("§7Range: §e" + slider.getMin() + " §7to §e" + slider.getMax());
        } else if (setting instanceof DropdownSetting dropdown) {
            sendMessage("§7Options: §e" + String.join(", ", dropdown.getOptions()));
        }
    }

    private void setSetting(Module module, SettingsProvider provider, String settingName, String value) {
        ModuleSetting setting = findSetting(provider, settingName);

        if (setting == null) {
            sendError("Setting not found: " + settingName);
            listAvailableSettings(provider);
            return;
        }

        try {
            if (setting instanceof CheckboxSetting checkboxSetting) {
                boolean boolValue = parseBoolean(value);
                checkboxSetting.setValue(boolValue);
                sendSuccess("Set §e" + setting.getName() + " §ato §f" + boolValue);

            } else if (setting instanceof SliderSetting slider) {
                float floatValue = Float.parseFloat(value);
                if (floatValue < slider.getMin() || floatValue > slider.getMax()) {
                    sendError("Value must be between " + slider.getMin() + " and " + slider.getMax());
                    return;
                }
                slider.setValue(floatValue);
                sendSuccess("Set §e" + setting.getName() + " §ato §f" + floatValue);

            } else if (setting instanceof TextBoxSetting textSetting) {
                textSetting.setValue(value);
                sendSuccess("Set §e" + setting.getName() + " §ato §f" + value);

            } else if (setting instanceof DropdownSetting dropdown) {
                // Try to set by option name
                String[] options = dropdown.getOptions();
                int index = -1;
                for (int i = 0; i < options.length; i++) {
                    if (options[i].equalsIgnoreCase(value)) {
                        index = i;
                        break;
                    }
                }

                if (index == -1) {
                    // Try parsing as index
                    try {
                        index = Integer.parseInt(value);
                        if (index < 0 || index >= options.length) {
                            sendError("Invalid option. Available: " + String.join(", ", options));
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError("Invalid option. Available: " + String.join(", ", options));
                        return;
                    }
                }

                dropdown.setSelectedIndex(index);
                sendSuccess("Set §e" + setting.getName() + " §ato §f" + dropdown.getSelectedOption());

            } else if (setting instanceof ColorPickerSetting colorPicker) {
                // Parse hex color
                colorPicker.setFromHex(value);
                sendSuccess("Set §e" + setting.getName() + " §ato §f#" + colorPicker.getHexRGB());

            } else {
                sendError("Unsupported setting type: " + setting.getClass().getSimpleName());
            }

            // Save config after changing settings
            try {
                dev.hunchclient.util.ConfigManager.save();
            } catch (Exception e) {
                sendWarning("Failed to save config: " + e.getMessage());
            }

        } catch (NumberFormatException e) {
            sendError("Invalid number format: " + value);
        } catch (Exception e) {
            sendError("Failed to set value: " + e.getMessage());
        }
    }

    private ModuleSetting findSetting(SettingsProvider provider, String name) {
        return provider.getSettings().stream()
            .filter(s -> s.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    private void listAvailableSettings(SettingsProvider provider) {
        List<String> settingNames = provider.getSettings().stream()
            .map(ModuleSetting::getName)
            .collect(Collectors.toList());

        if (!settingNames.isEmpty()) {
            sendMessage("§7Available settings: §e" + String.join(", ", settingNames));
        }
    }

    private String getSettingValueString(ModuleSetting setting) {
        if (setting instanceof CheckboxSetting checkbox) {
            return String.valueOf(checkbox.getValue());
        } else if (setting instanceof SliderSetting slider) {
            return slider.formatValue();
        } else if (setting instanceof TextBoxSetting textBox) {
            return textBox.getValue();
        } else if (setting instanceof DropdownSetting dropdown) {
            return dropdown.getSelectedOption();
        } else if (setting instanceof ColorPickerSetting colorPicker) {
            return "#" + colorPicker.getHexRGB();
        }
        return "unknown";
    }

    private String getSettingType(ModuleSetting setting) {
        if (setting instanceof CheckboxSetting) return "Boolean";
        if (setting instanceof SliderSetting) return "Number";
        if (setting instanceof TextBoxSetting) return "Text";
        if (setting instanceof DropdownSetting) return "Choice";
        if (setting instanceof ColorPickerSetting) return "Color";
        if (setting instanceof ButtonSetting) return "Button";
        return "Unknown";
    }

    private boolean parseBoolean(String value) {
        String lower = value.toLowerCase();
        return lower.equals("true") || lower.equals("yes") ||
               lower.equals("on") || lower.equals("enable") || lower.equals("1");
    }

    @Override
    public List<String> getSuggestions(String[] args) {
        ModuleManager manager = ModuleManager.getInstance();

        if (args.length == 1) {
            // Suggest modules that have settings
            return manager.getModules().stream()
                .filter(m -> m instanceof SettingsProvider)
                .map(Module::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Suggest settings for the module
            Module module = manager.getModuleByName(args[0]);
            if (module instanceof SettingsProvider provider) {
                return provider.getSettings().stream()
                    .map(ModuleSetting::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            // Suggest values based on setting type
            Module module = manager.getModuleByName(args[0]);
            if (module instanceof SettingsProvider provider) {
                ModuleSetting setting = findSetting(provider, args[1]);
                if (setting != null) {
                    List<String> suggestions = new ArrayList<>();

                    if (setting instanceof CheckboxSetting) {
                        suggestions.add("true");
                        suggestions.add("false");
                    } else if (setting instanceof DropdownSetting dropdown) {
                        suggestions.addAll(Arrays.asList(dropdown.getOptions()));
                    }

                    return suggestions.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }
        }

        return super.getSuggestions(args);
    }
}