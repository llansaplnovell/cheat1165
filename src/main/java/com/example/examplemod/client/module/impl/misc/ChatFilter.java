package com.example.examplemod.client.module.impl.misc;

import com.example.examplemod.client.api.event.EventHandler;
import com.example.examplemod.client.api.module.Category;
import com.example.examplemod.client.api.module.Module;
import com.example.examplemod.client.api.module.ModuleInfo;
import com.example.examplemod.client.api.setting.StringSetting;
import com.example.examplemod.client.event.PacketReceiveEvent;
import net.minecraft.network.play.server.SChatPacket;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Drops incoming chat packets whose text matches a regex, before they ever reach the chat log. */
@ModuleInfo(name = "Chat Filter", description = "Hides chat messages matching a pattern", category = Category.MISC)
public class ChatFilter extends Module {

    private final StringSetting pattern = register(new StringSetting("Pattern", ""));

    @EventHandler
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPacket() instanceof SChatPacket) || pattern.get().isEmpty()) {
            return;
        }
        String text = ((SChatPacket) event.getPacket()).getMessage().getString();
        try {
            if (Pattern.compile(pattern.get(), Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                event.cancel();
            }
        } catch (PatternSyntaxException ignored) {
            // invalid regex typed by the user: fail open, keep showing chat
        }
    }
}
