package com.instantsneak.client;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class InstantSneakConfigScreen {
    private InstantSneakConfigScreen() {
    }

    public static Screen create(Screen parent) {
        InstantSneakConfig config = InstantSneakConfig.get();
        InstantSneakConfig defaults = new InstantSneakConfig();

        return YetAnotherConfigLib.createBuilder()
                .title(literal("Instant Sneak"))
                .category(ConfigCategory.createBuilder()
                        .name(literal("General"))
                        .option(sneakHeightOption(config, defaults))
                        .option(booleanOption(
                                "Animate Normal Sneak",
                                "Animates the normal standing-to-sneak movement.",
                                defaults.animateShallowSneak,
                                () -> config.animateShallowSneak,
                                value -> config.animateShallowSneak = value
                        ))
                        .option(booleanOption(
                                "Animate Ceiling Sneak",
                                "Animates the movement when crouching under a ceiling.",
                                defaults.animateDeepSneak,
                                () -> config.animateDeepSneak,
                                value -> config.animateDeepSneak = value
                        ))
                        .option(booleanOption(
                                "Affect Third Person",
                                "Applies the height changes while using third-person view.",
                                defaults.affectThirdPerson,
                                () -> config.affectThirdPerson,
                                value -> config.affectThirdPerson = value
                        ))
                        .build())
                .save(InstantSneakConfig::save)
                .build()
                .generateScreen(parent);
    }

    private static Option<InstantSneakConfig.SneakHeight> sneakHeightOption(InstantSneakConfig config, InstantSneakConfig defaults) {
        return Option.<InstantSneakConfig.SneakHeight>createBuilder()
                .name(literal("Sneak Height"))
                .description(description("Changes the sneaking height."))
                .binding(defaults.sneakHeight, () -> config.sneakHeight, value -> config.sneakHeight = value)
                .controller(option -> EnumControllerBuilder.create(option)
                        .enumClass(InstantSneakConfig.SneakHeight.class))
                .build();
    }

    private static Option<Boolean> booleanOption(
            String name,
            String details,
            boolean defaultValue,
            Supplier<Boolean> getter,
            Consumer<Boolean> setter
    ) {
        return Option.<Boolean>createBuilder()
                .name(literal(name))
                .description(description(details))
                .binding(defaultValue, getter, setter)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static OptionDescription description(String text) {
        return OptionDescription.of(literal(text));
    }

    private static Component literal(String text) {
        return Component.literal(text);
    }
}
