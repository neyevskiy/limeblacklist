package me.neyevskiy.limeblacklist;

import com.google.gson.Gson;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Plugin(
        id = "limeblacklist",
        name = "LimeBlacklist",
        version = "1.0",
        authors = {"neyevskiy"}
)
public class LimeBlacklist {

    private final ProxyServer server;
    private final Path dataDirectory;
    private final Path blacklistFile;
    private final Path userCacheFile;

    private Set<UUID> blacklisted = new HashSet<>();
    private Map<String, UUID> userCache = new HashMap<>();

    @Inject
    public LimeBlacklist(ProxyServer server, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.dataDirectory = dataDirectory;
        this.blacklistFile = dataDirectory.resolve("blacklist.txt");
        this.userCacheFile = dataDirectory.resolve("usercache.json");

        loadBlacklist();
        loadUserCache();

        server.getEventManager().register(this, this);
        server.getCommandManager().register("blacklist", new BlacklistCommand());
    }

    private void loadBlacklist() {
        try {
            if (Files.exists(blacklistFile)) {
                for (String line : Files.readAllLines(blacklistFile)) {
                    try {
                        blacklisted.add(UUID.fromString(line.trim()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveBlacklist() {
        try {
            Files.write(blacklistFile,
                    blacklisted.stream().map(UUID::toString).toList(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadUserCache() {
        if (Files.exists(userCacheFile)) {
            try (BufferedReader reader = Files.newBufferedReader(userCacheFile)) {
                new Gson().<Map<String, String>>fromJson(reader, Map.class)
                        .forEach((name, uuid) -> {
                            try {
                                userCache.put(name.toLowerCase(), UUID.fromString(uuid));
                            } catch (IllegalArgumentException ignored) {}
                        });
            } catch (IOException ignored) {
            }
        }
    }

    private void saveUserCache() {
        Map<String, String> toSave = userCache.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
        try (BufferedWriter writer = Files.newBufferedWriter(userCacheFile)) {
            new Gson().toJson(toSave, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Subscribe
    public void onLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getUsername().toLowerCase();

        // Сохраняем ник и UUID
        userCache.put(name, uuid);
        saveUserCache();

        if (blacklisted.contains(uuid)) {
            player.disconnect(Component.text("ʙᴀш ᴀᴋᴋᴀʏʜт ᴏтᴋлючᴇʜ ᴀдмиʜиᴄтᴘᴀциᴇй").color(TextColor.color(255, 0, 0)));
        }
    }

    private class BlacklistCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (!source.hasPermission("limeblacklist.use")) {
                source.sendMessage(Component.text("У вас нет прав для этой команды."));
                return;
            }

            if (args.length == 0) {
                source.sendMessage(Component.text("Использование: /blacklist <ник> или /blacklist unlock <ник>"));
                return;
            }

            if (args[0].equalsIgnoreCase("unlock") && args.length == 2) {
                String name = args[1].toLowerCase();
                UUID uuid = userCache.get(name);

                if (uuid == null) {
                    source.sendMessage(Component.text("Ник не найден в кэше — игрок должен был быть в сети хотя бы раз.").color(TextColor.color(255, 0, 0)));
                    return;
                }

                blacklisted.remove(uuid);
                saveBlacklist();
                source.sendMessage(Component.text("Игрок разблокирован.").color(TextColor.color(72, 255, 0)));
                return;
            }

            if (args.length == 1) {
                String name = args[0];
                server.getPlayer(name).ifPresentOrElse(player -> {
                    UUID uuid = player.getUniqueId();
                    blacklisted.add(uuid);
                    userCache.put(player.getUsername().toLowerCase(), uuid);
                    saveBlacklist();
                    saveUserCache();
                    player.disconnect(Component.text("ʙᴀш ᴀᴋᴋᴀʏʜт ᴏтᴋлючᴇʜ ᴀдмиʜиᴄтᴘᴀциᴇй").color(TextColor.color(255, 0, 0)));
                    source.sendMessage(Component.text("Игрок добавлен в чёрный список.").color(TextColor.color(72, 255, 0)));
                }, () -> {
                    UUID uuid = userCache.get(name.toLowerCase());
                    if (uuid == null) {
                        source.sendMessage(Component.text("Игрок не найден в сети и не известен по нику.").color(TextColor.color(255, 0, 0)));
                        return;
                    }
                    blacklisted.add(uuid);
                    saveBlacklist();
                    source.sendMessage(Component.text("Игрок добавлен в чёрный список (оффлайн).").color(TextColor.color(72, 255, 0)));
                });
                return;
            }

            source.sendMessage(Component.text("Неверное использование команды."));
        }
    }
}
