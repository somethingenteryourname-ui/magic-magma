package dev.crystalline.resonance;

import dev.crystalline.resonance.command.CrystallineCommand;
import dev.crystalline.resonance.crystal.CrystalDropListener;
import dev.crystalline.resonance.crystal.CrystalSettings;
import dev.crystalline.resonance.crystal.PlacedBlockTracker;
import dev.crystalline.resonance.item.CustomItems;
import dev.crystalline.resonance.item.ItemProtectionListener;
import dev.crystalline.resonance.mana.ManaManager;
import dev.crystalline.resonance.recipe.RecipeManager;
import dev.crystalline.resonance.spell.CastListener;
import dev.crystalline.resonance.spell.SpellManager;
import dev.crystalline.resonance.util.Messages;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class CrystallineResonance extends JavaPlugin {

    private Keys keys;
    private Messages messages;
    private CrystalSettings crystalSettings;
    private CustomItems items;
    private ManaManager mana;
    private SpellManager spells;
    private RecipeManager recipes;
    private PlacedBlockTracker placedBlocks;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        keys = new Keys(this);
        messages = new Messages(this);
        crystalSettings = new CrystalSettings(this);
        items = new CustomItems(this);
        mana = new ManaManager(this);
        spells = new SpellManager(this);
        recipes = new RecipeManager(this);
        placedBlocks = new PlacedBlockTracker(keys.placedBlocks);

        loadSettings();
        recipes.registerAll();

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(mana, this);
        pluginManager.registerEvents(recipes, this);
        pluginManager.registerEvents(new CastListener(this), this);
        pluginManager.registerEvents(new CrystalDropListener(this), this);
        pluginManager.registerEvents(new ItemProtectionListener(this), this);

        PluginCommand command = getCommand("crystalline");
        if (command != null) {
            CrystallineCommand handler = new CrystallineCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        mana.start();
    }

    @Override
    public void onDisable() {
        if (mana != null) {
            mana.shutdown();
        }
        if (recipes != null) {
            recipes.unregisterAll();
        }
    }

    /** Re-reads config.yml and re-applies every setting, including recipes. */
    public void reload() {
        reloadConfig();
        loadSettings();
        recipes.unregisterAll();
        recipes.registerAll();
        getServer().updateRecipes();
        recipes.discoverForOnlinePlayers();
        mana.restart();
    }

    private void loadSettings() {
        messages.reload();
        crystalSettings.reload();
        mana.reload();
        spells.reload();
    }

    public Keys keys() {
        return keys;
    }

    public Messages messages() {
        return messages;
    }

    public CrystalSettings crystalSettings() {
        return crystalSettings;
    }

    public CustomItems items() {
        return items;
    }

    public ManaManager mana() {
        return mana;
    }

    public SpellManager spells() {
        return spells;
    }

    public RecipeManager recipes() {
        return recipes;
    }

    public PlacedBlockTracker placedBlocks() {
        return placedBlocks;
    }
}
