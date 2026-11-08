package com.example.neoforge;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ExampleMod.MOD_ID)
public class ExampleMod {
    public static final String MOD_ID = "examplemod";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ExampleMod() {
        LOGGER.info("Hello from a NeoForge mod launched by HeadlessMc!");
    }

}
