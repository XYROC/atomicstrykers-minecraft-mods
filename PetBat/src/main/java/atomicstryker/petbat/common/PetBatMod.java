package atomicstryker.petbat.common;

import atomicstryker.petbat.client.ClientProxy;
import atomicstryker.petbat.common.network.BatNamePacket;
import atomicstryker.petbat.common.network.NetworkHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ObjectHolder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;

@Mod(PetBatMod.MOD_ID)
public class PetBatMod {

    static final String MOD_ID = "petbat";
    public static Logger LOGGER;

    private static PetBatMod instance;
    public static final IProxy proxy = DistExecutor.runForDist(() -> () -> new ClientProxy(), () -> () -> new ServerProxy());

    @ObjectHolder("death")
    public static final SoundEvent soundDeath = null;

    @ObjectHolder("hit")
    public static final SoundEvent soundHit = null;

    @ObjectHolder("idle")
    public static final SoundEvent soundIdle = null;

    @ObjectHolder("loop")
    public static final SoundEvent soundLoop = null;

    @ObjectHolder("takeoff")
    public static final SoundEvent soundTakeoff = null;


    private final String[] batNames = {"Lucius", "Draco", "Vlad", "Darkwing", "Zubat", "Cecil", "Dragos", "Cezar", "Ciprian", "Daniel", "Dorin", "Mihai", "Mircea", "Radu"};

    public EntityType batEntityType;
    public Item itemPocketedBat;
    public PetBatConfig config;
    public Item itemBatFlute;

    public NetworkHelper networkHelper;

    protected File configFile;
    private Item TAME_ITEM_ID;

    private Field entityBatFlightCoords;


    public static PetBatMod instance() {
        return instance;
    }

    /**
     * Create a {@link SoundEvent}.
     *
     * @param soundName The SoundEvent's name without the modid prefix
     * @return The SoundEvent
     */
    private static SoundEvent createSoundEvent(String soundName) {
        final ResourceLocation soundID = new ResourceLocation("petbat", soundName);
        return new SoundEvent(soundID).setRegistryName(soundID);
    }

    /**
     * experience to levels table 0 - no xp 25 - lvl 1, 25 xp diff 75 - lvl 2,
     * 50 xp diff 175 - lvl 3, 100 xp diff 375 - lvl 4, 200 xp diff 775 - lvl 5,
     * 400 xp diff 1575 - lvl 6, 800 xp diff
     */
    public int getLevelFromExperience(int xp) {
        if (xp < 25)
            return 0;
        if (xp < 75)
            return 1;
        if (xp < 175)
            return 2;
        if (xp < 375)
            return 3;
        if (xp < 775)
            return 4;
        if (xp < 1575)
            return 5;
        return 6;
    }

    public int getMissingExperienceToNextLevel(int xp) {
        if (xp < 25)
            return 25 - xp;
        if (xp < 75)
            return 75 - xp;
        if (xp < 175)
            return 175 - xp;
        if (xp < 375)
            return 375 - xp;
        if (xp < 775)
            return 775 - xp;
        if (xp < 1575)
            return 1575 - xp;
        return -1;
    }

    public String getLevelTitle(int level) {
        return I18n.format("translation.PetBat:batlevel" + level);
    }

    public String getLevelDescription(int level) {
        return I18n.format("translation.PetBat:batlevel" + level + "desc");
    }

    @SubscribeEvent
    public void serverStarted(FMLServerStartingEvent evt) {

        configFile = new File(proxy.getMcFolder(), File.separatorChar + "config" + File.separatorChar + "petbat.cfg");
        loadConfig();
    }

    private void loadConfig() {
        PetBatConfig defaultConfig = new PetBatConfig();
        defaultConfig.setBatInventoryTeleport(true);

        config = GsonConfig.loadConfigWithDefault(PetBatConfig.class, configFile, defaultConfig);
    }

    public PetBatMod() {
        instance = this;
        MinecraftForge.EVENT_BUS.register(this);
        FMLJavaModLoadingContext.get().getModEventBus().register(this);
        LOGGER = LogManager.getLogger();
    }

    @SubscribeEvent
    public void registerSoundEvent(RegistryEvent.Register<SoundEvent> event) {
        event.getRegistry().registerAll(createSoundEvent("death"), createSoundEvent("hit"), createSoundEvent("idle"), createSoundEvent("loop"), createSoundEvent("takeoff"));
    }

    @SubscribeEvent
    public void registerItemEvent(RegistryEvent.Register<Item> event) {
        itemPocketedBat = new ItemPocketedPetBat().setRegistryName("petbat", "fed_pet_bat");
        event.getRegistry().register(itemPocketedBat);

        itemBatFlute = new ItemBatFlute().setRegistryName("petbat", "bat_flute");
        event.getRegistry().register(itemBatFlute);
    }

    @SubscribeEvent
    public void registerEntityEvent(RegistryEvent.Register<EntityType<?>> event) {
        batEntityType = EntityType.Builder.create((type, world) -> new EntityPetBat(world), EntityClassification.CREATURE).size(0.5F, 0.9F).setTrackingRange(32).setUpdateInterval(1).setShouldReceiveVelocityUpdates(false).build("petbat").setRegistryName("petbat");
        event.getRegistry().register(batEntityType);
    }

    @SubscribeEvent
    public void preInit(FMLCommonSetupEvent event) {

        TAME_ITEM_ID = Items.PUMPKIN_PIE;

        networkHelper = new NetworkHelper("petbat", BatNamePacket.class);

        for (Field f : BatEntity.class.getDeclaredFields()) {
            if (BlockPos.class.isAssignableFrom(f.getType())) {
                entityBatFlightCoords = f;
                entityBatFlightCoords.setAccessible(true);
            }
        }

        proxy.onModPreInit();
    }

    @SubscribeEvent
    public void clientInit(FMLClientSetupEvent event) {
        proxy.onClientInit();
    }

    public boolean getPetBatInventoryTeleportEnabled() {
        return config.isBatInventoryTeleport();
    }

    @SubscribeEvent
    public void onPlayerLeftClick(BreakSpeed event) {
        PlayerEntity p = event.getPlayer();
        ItemStack item = p.inventory.getCurrentItem();
        if (!p.world.isRemote && item.getItem() == TAME_ITEM_ID) {
            List<Entity> entityList = p.world.getEntitiesWithinAABBExcludingEntity(p, p.getBoundingBox().grow(10D, 10D, 10D));
            BlockPos coords = new BlockPos((int) (p.posX + 0.5D), (int) (p.posY + 1.5D), (int) (p.posZ + 0.5D));
            entityList.stream().filter(ent -> ent instanceof BatEntity).forEach(ent -> {
                try {
                    entityBatFlightCoords.set(ent, coords);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    @SubscribeEvent
    public void onEntityInteract(EntityInteractSpecific event) {
        if (event.getTarget() instanceof BatEntity) {
            PlayerEntity p = event.getPlayer();
            if (!p.world.isRemote && event.getTarget().isAlive()) {
                ItemStack item = p.inventory.getCurrentItem();
                if (item.getItem() == TAME_ITEM_ID) {
                    event.setCanceled(true);
                    p.inventory.clearMatchingItems((itemStack) -> (itemStack.getItem() == TAME_ITEM_ID), 1);

                    BatEntity b = (BatEntity) event.getTarget();
                    EntityPetBat newPet = new EntityPetBat(p.world);
                    newPet.setLocationAndAngles(b.posX, b.posY, b.posZ, b.rotationYaw, b.rotationPitch);
                    newPet.setNames(p.getUniqueID(), getRandomBatName());
                    newPet.setOwnerEntity(p);

                    p.world.addEntity(newPet);
                    b.setHealth(0F);
                    b.remove();
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerAttacksEntity(AttackEntityEvent event) {
        if (event.getTarget() instanceof EntityPetBat) {
            EntityPetBat bat = (EntityPetBat) event.getTarget();
            if (bat.getOwnerUUID() == null) {
                bat.setOwnerEntity(event.getPlayer());
                bat.setNames(event.getPlayer().getUniqueID(), "Battus Genericus");
            }
            if (event.getPlayer().getUniqueID().equals(bat.getOwnerUUID()) && event.getPlayer().getHeldItemMainhand() == ItemStack.EMPTY) {
                bat.recallToOwner();
                event.setCanceled(true);
            }
        }
    }

    private String getRandomBatName() {
        return batNames[new Random().nextInt(batNames.length)];
    }

    @SuppressWarnings("rawtypes")
    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        // System.out.println("onItemTossEvent hooked, isRemote: " +
        // event.getEntity().world.isRemote);
        if (!event.getEntity().world.isRemote) {
            ItemEntity itemDropped = event.getEntityItem();
            // System.out.println("PlayerDropsEvent iterating over drop " +
            // itemDropped);
            ItemEntity foundItem;
            final Item id = itemDropped.getItem().getItem();
            if (id == itemPocketedBat) {
                final EntityPetBat bat = ItemPocketedPetBat.toBatEntity(itemDropped.world, itemDropped.getItem(), event.getPlayer());
                if (bat.getHealth() > 1) {
                    bat.setPosition(itemDropped.posX, itemDropped.posY, itemDropped.posZ);
                    itemDropped.world.addEntity(bat);
                    event.setCanceled(true);
                } else {
                    // bat is inert. see if it was tossed onto pumpkin pie for
                    // revival

                    final List nearEnts = itemDropped.world.getEntitiesWithinAABBExcludingEntity(itemDropped, itemDropped.getBoundingBox().grow(8D, 8D, 8D));
                    for (Object o : nearEnts) {
                        if (o instanceof ItemEntity) {
                            foundItem = (ItemEntity) o;
                            if (foundItem.getItem().getItem() == TAME_ITEM_ID) {
                                bat.setPosition(itemDropped.posX, itemDropped.posY, itemDropped.posZ);
                                itemDropped.world.addEntity(bat);
                                bat.setHealth(bat.getMaxHealth()); // set full
                                // entity
                                // health
                                event.setCanceled(true);
                                foundItem.getItem().shrink(1);
                                if (foundItem.getItem().getCount() < 1) {
                                    foundItem.remove(); // destroy pie item
                                }
                                break;
                            }
                        }
                    }
                }
            } else if (id == TAME_ITEM_ID) {
                final List nearEnts = itemDropped.world.getEntitiesWithinAABBExcludingEntity(itemDropped, itemDropped.getBoundingBox().grow(8D, 8D, 8D));
                for (Object o : nearEnts) {
                    if (o instanceof EntityPetBat) {
                        final EntityPetBat bat = (EntityPetBat) o;
                        if ((bat.getAttackTarget() == null || !bat.getAttackTarget().isAlive()) && (bat.getFoodAttackTarget() == null || bat.getFoodAttackTarget().isAlive())) {
                            bat.setFoodAttackTarget(itemDropped);
                            break;
                        }
                    } else if (o instanceof ItemEntity) {
                        foundItem = (ItemEntity) o;
                        if (foundItem.getItem().getItem() == itemPocketedBat) // inert
                        // bat
                        // lying
                        // around
                        {
                            final EntityPetBat bat = ItemPocketedPetBat.toBatEntity(foundItem.world, foundItem.getItem(), event.getPlayer());
                            bat.setPosition(foundItem.posX, foundItem.posY, foundItem.posZ);
                            foundItem.world.addEntity(bat);
                            bat.setHealth(bat.getMaxHealth()); // set full
                            // entity health
                            event.setCanceled(true);
                            foundItem.remove(); // destroy bat item
                            break;
                        }
                    }
                }
            } else if (id == itemBatFlute) // bat flutes cannot be dropped. ever.
            {
                event.setCanceled(true);
                // as this only stops the entity from entering the world, we
                // need to re-add the item to the inventory
                event.getPlayer().addItemStackToInventory(event.getEntityItem().getItem());
            }
        }
    }

    @SubscribeEvent
    public void onPlayerDropsEvent(LivingDropsEvent event) {
        // iterate drops, remove all batflutes
        event.getDrops().removeIf(itemEntity -> itemEntity.getItem().getItem() == itemBatFlute);
    }

    @SubscribeEvent
    public void onEntityLivingUpdate(LivingUpdateEvent event) {
        if (event.getEntityLiving() instanceof PlayerEntity) {
            PlayerEntity p = (PlayerEntity) event.getEntityLiving();
            if (p.isAlive() && p.getHeldItemMainhand().getItem().equals(itemPocketedBat)) {
                if (p.getActivePotionEffect(Effects.NIGHT_VISION) == null) {
                    p.addPotionEffect(new EffectInstance(Effects.NIGHT_VISION, 100));
                }
            }
        }
    }

    public ItemStack removeFluteFromPlayer(PlayerEntity player, String petName) {
        for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
            ItemStack item = player.inventory.mainInventory.get(i);
            if (item.getItem() == itemBatFlute) {
                if (item.getTag().getString("batName").equals(petName)) {
                    player.inventory.mainInventory.set(i, ItemStack.EMPTY);
                    return item;
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
