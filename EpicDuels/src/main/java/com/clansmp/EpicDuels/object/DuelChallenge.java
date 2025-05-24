package com.clansmp.EpicDuels.object;

import com.clansmp.EpicDuels.EpicDuels;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class DuelChallenge {

    private final Player challenger;
    private final Player challenged;
    private final EpicDuels plugin;
    private BukkitRunnable timeoutTask;
    private boolean keepInventory; // <--- NEW FIELD

    public DuelChallenge(Player challenger, Player challenged, EpicDuels plugin) {
        this.challenger = challenger;
        this.challenged = challenged;
        this.plugin = plugin;
        this.keepInventory = false; // Default value
    }

    public Player getChallenger() {
        return challenger;
    }

    public Player getChallenged() {
        return challenged;
    }

    public boolean isKeepInventory() { // <--- NEW GETTER
        return keepInventory;
    }

    public void setKeepInventory(boolean keepInventory) { // <--- NEW SETTER
        this.keepInventory = keepInventory;
    }

    public void startTimeout() {
        long timeoutTicks = 30 * 20L; // 30 seconds

        timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Only cancel if both players are still online and the challenge is still active
                if (challenger.isOnline() && challenged.isOnline() &&
                    plugin.getDuelManager().getOutgoingChallengeByPlayer(challenger) == DuelChallenge.this) {

                    plugin.getLogger().info("Duel challenge between " + challenger.getName() + " and " + challenged.getName() + " timed out.");

                    challenger.sendMessage(Component.text("Your duel challenge to " + challenged.getName() + " timed out.").color(NamedTextColor.RED));
                    challenged.sendMessage(Component.text("The duel challenge from " + challenger.getName() + " timed out.").color(NamedTextColor.RED));

                    plugin.getDuelManager().cancelChallenge(DuelChallenge.this);
                }
            }
        };
        timeoutTask.runTaskLater(plugin, timeoutTicks);
        plugin.getLogger().info("Duel challenge timeout started for " + challenger.getName() + " to " + challenged.getName() + ".");
    }

    public void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
            plugin.getLogger().info("Duel challenge timeout cancelled.");
        }
    }
}