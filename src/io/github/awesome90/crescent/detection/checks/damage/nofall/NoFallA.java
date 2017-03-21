package io.github.awesome90.crescent.detection.checks.damage.nofall;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import io.github.awesome90.crescent.Crescent;
import io.github.awesome90.crescent.behaviour.Behaviour;
import io.github.awesome90.crescent.detection.checks.Check;
import io.github.awesome90.crescent.detection.checks.CheckVersion;
import io.github.awesome90.crescent.info.Profile;

public class NoFallA extends CheckVersion {

	/**
	 * The total damage that a player should have taken.
	 */
	private double totalDamage;
	/**
	 * The difference the damage that a player should have taken and the damage
	 * that they actually took.
	 */
	private double totalDisplacedHealth;

	public NoFallA(Check check) {
		super(check, "A", "Checks the damage that the player took compared to the damage that they should have taken.");
		this.totalDamage = totalDisplacedHealth = 0.0;
		this.totalDisplacedHealth = 0.0;
	}

	@Override
	public void call(Event event) {
		if (event instanceof PlayerMoveEvent) {
			final PlayerMoveEvent pme = (PlayerMoveEvent) event;

			final Player player = profile.getPlayer();

			if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {

				final Material from = pme.getFrom().getBlock().getRelative(BlockFace.DOWN).getType();
				final Material to = pme.getTo().getBlock().getRelative(BlockFace.DOWN).getType();

				final double fallDistance = profile.getBehaviour().getFallDistance();

				if (fallDistance < 4.0) {
					/*
					 * The player has not fallen enough distance to take fall
					 * damage.
					 */
					return;
				}

				// Bukkit.broadcastMessage("from: " + from.toString() + ", to: "
				// + to.toString());

				// The player has fallen far enough to take damage.

				final Behaviour behaviour = profile.getBehaviour();

				// Check if player has moved from air to ground.
				if (from == Material.AIR && to != Material.AIR) {

					if (!behaviour.isInWater() && !behaviour.isInWeb() && !player.isInsideVehicle()
							&& !player.isSleeping()) {

						/*
						 * Their expected health cannot be higher than their
						 * maximum allowed health and cannot be lower then zero.
						 */
						final double expected = Math
								.max(Math.min(player.getHealth() - getExpectedDamage(profile, fallDistance),
										player.getMaxHealth()), 0.0);

						/*
						 * Check a bit later if their health is higher than it
						 * is expected to be.
						 */
						Bukkit.getScheduler().runTaskLater(Crescent.getInstance(), () -> {
							if (player.getHealth() > expected) {
								callback(true);
							}
						}, 5L);

					}
				}
			}
		}
	}

	@Override
	public double checkCurrentCertainty() {
		return (totalDisplacedHealth / totalDamage) * 100.0;
	}

	/**
	 * @param profile
	 *            The profile of the player.
	 * @param fallDistance
	 *            The distance the player has fallen.
	 * @return How much their armour EPF reduces their fall damage.
	 */
	private double getExpectedDamage(Profile profile, double fallDistance) {
		final Player player = profile.getPlayer();

		/*
		 * Some formulae used from here:
		 * http://www.minecraftforum.net/forums/minecraft-discussion/survival-
		 * mode/2577601-facts-fall-damage
		 * 
		 * Thank you to beaudigi for making this!
		 */

		/*
		 * Always round up to avoid false positives.
		 * 
		 * Take the smallest out of the calculated and the player's maximum
		 * health.
		 */
		double damage = Math.min(
				Math.ceil(Math.max(profile.getBehaviour().getLastY() - player.getLocation().getY(), fallDistance)) - 2,
				profile.getPlayer().getMaxHealth());

		// Check to see if the player has feather falling.
		double epf = 0.0;
		// The amount of damage reduction enchantments give.
		double enchantmentReduction = 0.0;

		final ItemStack boots = player.getInventory().getBoots();
		if (boots != null) {
			if (boots.containsEnchantment(Enchantment.PROTECTION_FALL)) {
				final int level = boots.getEnchantmentLevel(Enchantment.PROTECTION_FALL);
				// 2.5 is the type modifier for feather falling.
				epf += getEPFExtra(level, 2.5);
			}

			if (boots.containsEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL)) {
				final int level = boots.getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL);
				// 0.75 is the type modifier for protection.
				epf += getEPFExtra(level, 0.75);
			}

			// Cap the EPF at 20.
			enchantmentReduction = Math.min(epf, 20.0) * 4;
		}

		double potionReduction = 0.0;

		for (PotionEffect effect : player.getActivePotionEffects()) {
			if (effect.getType().equals(PotionEffectType.DAMAGE_RESISTANCE)) {
				potionReduction += damage - ((1 - effect.getAmplifier() * 0.2) * fallDistance - 3);
			}
			if (effect.getType().equals(PotionEffectType.JUMP)) {
				potionReduction += damage - (fallDistance - 3 - effect.getAmplifier());
			}
		}

		// The maths just works, ok? :D
		return damage - enchantmentReduction - potionReduction - 1;
	}

	private double getEPFExtra(int level, double typeModifier) {
		return (6 + level * level) * typeModifier / 3.0;
	}

}
