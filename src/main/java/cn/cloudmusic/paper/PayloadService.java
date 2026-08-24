package cn.cloudmusic.paper;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Set;

final class PayloadService {
    static final String PREFIX = "cloudmusic_sync:";
    private static final Set<String> CHANNELS = Set.of("select_song", "playlist_import", "next_song", "clear_queue", "queue_action", "playback_mode");
    private final JavaPlugin plugin;
    private MusicManager music;

    PayloadService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void setMusic(MusicManager music) {
        this.music = music;
    }

    void register() {
        for (String channel : CHANNELS) {
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, PREFIX + channel, (incoming, player, data) -> {
                if (incoming.equals(PREFIX + channel)) plugin.getServer().getScheduler().runTask(plugin, () -> receive(channel, player, data));
            });
        }
        for (String channel : new String[]{"play", "stop", "volume", "auth", "hud", "queue", "playlist_open", "menu_open", "search_open"}) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, PREFIX + channel);
        }
    }

    void send(Player player, String channel, byte[] data) {
        if (player.isOnline()) player.sendPluginMessage(plugin, PREFIX + channel, data);
    }

    void broadcast(String channel, byte[] data) {
        for (Player player : plugin.getServer().getOnlinePlayers()) send(player, channel, data);
    }

    private void receive(String channel, Player player, byte[] data) {
        try {
            switch (channel) {
                case "select_song" -> {
                    player.sendMessage("§7服务端已收到歌曲资料，正在校验……");
                    music.select(player, Codec.readString(data, 4096));
                }
                case "playlist_import" -> {
                    player.sendMessage("§7服务端已收到歌单资料，正在校验……");
                    music.importPlaylist(player, Codec.readString(data, 786432));
                }
                case "next_song" -> music.next(player);
                case "clear_queue" -> music.clear(player);
                case "queue_action" -> {
                    Codec.PacketReader reader = Codec.reader(data);
                    music.queueAction(player, reader.varInt(), reader.varInt());
                }
                case "playback_mode" -> music.setMode(player, Codec.reader(data).varInt());
            }
        } catch (IOException | RuntimeException exception) {
            player.sendMessage("§c服务端接收失败：数据无法读取，请确认客户端与服务器版本匹配");
        }
    }
}
