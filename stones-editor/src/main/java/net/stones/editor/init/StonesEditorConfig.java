package net.stones.editor.init;

import net.minecraftforge.common.ForgeConfigSpec;

public class StonesEditorConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<String> ACTIVE_WORKSPACE_PACK;

    static {
        BUILDER.push("Workspace");
        ACTIVE_WORKSPACE_PACK = BUILDER
                .comment("Der Name des aktiven Projektordners oder der ZIP-Datei im '.minecraft/datapacks' Ordner.",
                         "Dieses Pack wird automatisch mit höchster Priorität geladen.",
                         "Leer lassen, um die Injektion vollständig zu deaktivieren.")
                .define("activeWorkspacePack", "");
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}