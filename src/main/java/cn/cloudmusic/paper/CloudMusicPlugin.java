package cn.cloudmusic.paper;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CloudMusicPlugin extends JavaPlugin implements Listener {
    private static final long MOD_CHECK_DELAY = 100L; // 5 秒（tick），给客户端留出频道注册时间
    private final Set<UUID> modReady = ConcurrentHashMap.newKeySet();
    private MusicManager music;

    @Override
    public void onEnable() {
        PayloadService payloads = new PayloadService(this);
        music = new MusicManager(this, payloads);
        payloads.setMusic(music);
        payloads.register();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, music::tick, 1L, 1L);
        getLogger().info("云听歌 Paper 插件已加载");
    }

    @Override
    public void onDisable() {
        if (music != null) music.shutdown();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        modReady.remove(id);
        // 延迟检查：装了 mod 的客户端进服后 1 秒内就会注册 cloudmusic_sync 频道
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player player = Bukkit.getPlayer(id);
            if (player == null || modReady.contains(id)) return;
            player.sendMessage("§e[云听歌] 检测到你的客户端未安装云听歌 mod，无法收听全服点歌的音乐。");
            player.sendMessage("§7你可以正常游玩，安装云听歌 mod 后重新进服即可参与同步听歌。");
        }, MOD_CHECK_DELAY);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        modReady.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChannelRegister(PlayerRegisterChannelEvent event) {
        // PlayerJoinEvent 时 Fabric 客户端还未发送 minecraft:register，消息会被丢弃；
        // 等客户端注册 play 频道（mod 就绪信号）后再同步当前歌曲与队列
        if (!event.getChannel().equals(PayloadService.PREFIX + "play")) return;
        modReady.add(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(this, () -> music.sync(event.getPlayer()));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("music")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令只能由玩家使用");
            return true;
        }
        if (!player.hasPermission("cloudmusic.use")) return true;
        music.command(player, args);
        return true;
    }
}
