package cn.cloudmusic.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class MusicManager {
    private static final int MAX_QUEUE = 500;
    private final JavaPlugin plugin;
    private final PayloadService payloads;
    private final Deque<Song> queue = new ArrayDeque<>();
    private final Map<UUID, Long> selectCooldown = new HashMap<>();
    private final Map<UUID, Long> importCooldown = new HashMap<>();
    private Song current;
    private long startedAt;
    private int mode;

    MusicManager(JavaPlugin plugin, PayloadService payloads) {
        this.plugin = plugin;
        this.payloads = payloads;
    }

    void tick() {
        if (current == null || startedAt + current.duration() > System.currentTimeMillis()) return;
        if (mode == 2) {
            start(current, "§b♫ 单曲循环：");
            return;
        }
        if (queue.isEmpty()) {
            current = null;
            payloads.broadcast("stop", Codec.empty());
            payloads.broadcast("queue", queueData());
            return;
        }
        if (mode == 1) queue.addLast(current);
        Song next = mode == 3 ? randomSong() : queue.removeFirst();
        start(next, "§b♫ 自动播放：");
    }

    void sync(Player player) {
        if (current != null && startedAt + current.duration() > System.currentTimeMillis()) {
            payloads.send(player, "play", playData());
        }
        payloads.send(player, "queue", queueData());
    }

    void command(Player player, String[] args) {
        String action = args.length == 0 ? "menu" : args[0].toLowerCase();
        switch (action) {
            case "play" -> open(player, "search_open", join(args, 1));
            case "next" -> next(player);
            case "now" -> player.sendMessage(current == null ? "§7当前没有播放歌曲" : "§b♫ " + current.title() + " - " + current.artist());
            case "login" -> sendInt(player, "auth", 0);
            case "account" -> sendInt(player, "auth", 1);
            case "logout" -> sendInt(player, "auth", 2);
            case "hud" -> open(player, "hud", null);
            case "playlist" -> open(player, "playlist_open", null);
            case "volume" -> {
                if (args.length > 1) {
                    sendInt(player, "volume", 1, clamp(parseInt(args[1], 100), 0, 100));
                } else {
                    sendInt(player, "volume", 0, 0);
                }
            }
            case "mute" -> sendInt(player, "volume", 2, 0);
            default -> open(player, "menu_open", null);
        }
    }

    void select(Player player, String json) {
        if (!cooldown(selectCooldown, player.getUniqueId(), 750)) {
            player.sendMessage("§e服务端接收失败：点歌请求过于频繁，请稍后再试");
            return;
        }
        Song song = parseSong(json);
        if (song == null) {
            player.sendMessage("§c服务端接收失败：歌曲资料无效");
            return;
        }
        if (contains(song.id())) {
            player.sendMessage("§e服务端接收失败：这首歌已经在播放或待播队列中");
            return;
        }
        song = song.withRequester(player.getName());
        if (current == null) {
            start(song, "§b♫ " + player.getName() + " 点播了：");
            player.sendMessage("§a服务端接收成功：已开始播放《" + song.title() + "》");
        }
        else if (queue.size() < MAX_QUEUE) {
            queue.addLast(song);
            broadcastQueue();
            announce("§a♫ " + player.getName() + " 将 " + song.title() + " - " + song.artist() + " 加入播放列表");
            player.sendMessage("§a服务端接收成功：歌曲《" + song.title() + "》已加入播放队列");
        } else {
            player.sendMessage("§c服务端接收失败：播放队列已达到 500 首上限");
        }
    }

    void importPlaylist(Player player, String json) {
        if (!cooldown(importCooldown, player.getUniqueId(), 5000)) {
            player.sendMessage("§e服务端接收失败：歌单导入请求过于频繁，请稍后再试");
            return;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonArray tracks = root.isJsonObject() ? root.getAsJsonObject().getAsJsonArray("tracks") : root.getAsJsonArray();
            if (tracks == null) {
                player.sendMessage("§c服务端接收失败：歌单资料缺少歌曲列表");
                return;
            }
            Set<String> seen = new HashSet<>();
            int added = 0;
            for (JsonElement element : tracks) {
                if (added >= MAX_QUEUE - queue.size()) break;
                Song song = parseSong(element.toString());
                if (song != null && seen.add(song.id()) && !contains(song.id())) {
                    queue.addLast(song.withRequester(player.getName()));
                    added++;
                }
            }
            if (current == null && !queue.isEmpty()) start(queue.removeFirst(), "§b♫ 歌单开始播放：");
            else broadcastQueue();
            if (added == 0) {
                player.sendMessage("§e服务端接收失败：歌单中没有可加入播放队列的歌曲");
            } else {
                player.sendMessage("§a服务端接收成功：已接收并导入 " + added + " 首歌曲");
            }
        } catch (RuntimeException exception) {
            player.sendMessage("§c服务端接收失败：歌单资料无效");
        }
    }

    void next(Player player) {
        if (queue.isEmpty()) {
            player.sendMessage("§e播放列表中没有下一首歌曲");
            return;
        }
        if (mode == 1 && current != null) queue.addLast(current);
        start(mode == 3 ? randomSong() : queue.removeFirst(), "§b♫ 已切换到下一首：");
    }

    void clear(Player player) {
        current = null;
        queue.clear();
        payloads.broadcast("stop", Codec.empty());
        broadcastQueue();
        announce("§7♫ " + player.getName() + " 清空了当前歌曲和播放队列");
    }

    void queueAction(Player player, int action, int index) {
        if (index < 0 || index >= queue.size()) return;
        Song selected = new ArrayList<>(queue).get(index);
        if (action == 0) queue.remove(selected);
        if (action == 1) {
            queue.remove(selected);
            start(selected, "§b♫ " + player.getName() + " 立即播放：");
            return;
        }
        broadcastQueue();
    }

    void setMode(Player player, int nextMode) {
        if (nextMode < 0 || nextMode > 3) return;
        mode = nextMode;
        broadcastQueue();
    }

    void shutdown() {
        queue.clear();
        current = null;
    }

    private void start(Song song, String message) {
        current = song;
        startedAt = System.currentTimeMillis() + 2500;
        announce(message + song.title() + " - " + song.artist());
        payloads.broadcast("play", playData());
        broadcastQueue();
    }

    private byte[] playData() {
        String audio = "https://music.163.com/song/media/outer/url?id=" + current.id() + ".mp3";
        return Codec.play(current.id(), current.title(), current.artist(), audio, current.cover(), startedAt, current.duration());
    }

    private byte[] queueData() {
        JsonObject root = new JsonObject();
        root.addProperty("mode", mode);
        JsonArray songs = new JsonArray();
        for (Song song : queue) {
            JsonObject item = new JsonObject();
            item.addProperty("id", song.id());
            item.addProperty("title", song.title());
            item.addProperty("artist", song.artist());
            item.addProperty("duration", song.duration());
            item.addProperty("requester", song.requester());
            songs.add(item);
        }
        root.add("songs", songs);
        return Codec.queue(root.toString());
    }

    private void broadcastQueue() {
        payloads.broadcast("queue", queueData());
    }

    private void announce(String message) {
        for (Player player : plugin.getServer().getOnlinePlayers()) player.sendMessage(message);
    }

    private void open(Player player, String channel, String value) {
        payloads.send(player, channel, value == null ? Codec.empty() : Codec.strings(value));
    }

    private void sendInt(Player player, String channel, int... values) {
        payloads.send(player, channel, Codec.ints(values));
    }

    private Song parseSong(String json) {
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            String id = value(object, "songId", "id");
            String title = clean(value(object, "title", "name"), 128);
            String artist = clean(value(object, "artist", "artists"), 128);
            String cover = value(object, "coverUrl", "cover");
            long duration = object.has("durationMillis") ? object.get("durationMillis").getAsLong() : object.has("duration") ? object.get("duration").getAsLong() : 0;
            if (!id.matches("[1-9][0-9]{0,18}") || title.isBlank() || artist.isBlank() || duration < 1000 || duration > 7200000 || (!cover.isBlank() && !validUrl(cover))) return null;
            return new Song(id, title, artist, cover, duration, "");
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String value(JsonObject object, String first, String second) {
        return object.has(first) ? object.get(first).getAsString() : object.has(second) ? object.get(second).getAsString() : "";
    }

    private static String clean(String value, int max) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\p{Cntrl}§]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.substring(0, Math.min(cleaned.length(), max));
    }

    private static boolean validUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null && (host.equals("music.163.com") || host.endsWith(".music.163.com") || host.equals("music.126.net") || host.endsWith(".music.126.net"));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean contains(String id) {
        return current != null && current.id().equals(id) || queue.stream().anyMatch(song -> song.id().equals(id));
    }

    private Song randomSong() {
        ArrayList<Song> songs = new ArrayList<>(queue);
        Collections.shuffle(songs);
        Song result = songs.getFirst();
        queue.remove(result);
        return result;
    }

    private static boolean cooldown(Map<UUID, Long> map, UUID uuid, long duration) {
        long now = System.currentTimeMillis();
        if (now < map.getOrDefault(uuid, 0L)) return false;
        map.put(uuid, now + duration);
        return true;
    }

    private static String join(String[] values, int start) {
        return String.join(" ", java.util.Arrays.copyOfRange(values, start, values.length)).trim();
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException exception) { return fallback; }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Song(String id, String title, String artist, String cover, long duration, String requester) {
        Song withRequester(String name) { return new Song(id, title, artist, cover, duration, name); }
    }
}
