package com.darkkyoung.beddefense;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/**
 * Uses vanilla's 3x3 DispenserScreen (rather than a global crafting recipe).
 * Nine REAL deposit slots; client-only 50% ghost hints are never actual items.
 * All unconsumed inputs are returned on close, including failed recipes.
 */
public final class RecoveryDispenserMenu extends DispenserMenu {
    private final SimpleContainer deposits;
    private final ServerLevel level;
    private final BlockPos beacon;
    private final UUID owner;
    private boolean drained;

    public RecoveryDispenserMenu(int containerId,Inventory inventory,ServerLevel level,BlockPos beacon,UUID owner){
        this(containerId,inventory,new SimpleContainer(9),level,beacon,owner);
    }
    private RecoveryDispenserMenu(int id,Inventory inventory,SimpleContainer deposits,
                                  ServerLevel level,BlockPos beacon,UUID owner){
        super(id,inventory,deposits);
        this.deposits=deposits;this.level=level;this.beacon=beacon.immutable();this.owner=owner;
    }
    public UUID owner(){return owner;}
    public BlockPos beacon(){return beacon;}
    public ServerLevel level(){return level;}
    public boolean ingredientsReady(boolean blocks){
        for(int i=0;i<9;i++){
            ItemStack item=deposits.getItem(i);
            if(item.isEmpty())return false;
            if(i==4){
                if(SpawnBedPrototype.isPortable(item) ||
                        !(net.minecraft.world.level.block.Block.byItem(item.getItem()) instanceof
                                net.minecraft.world.level.block.BedBlock))return false;
            }else if(!item.is(blocks?net.minecraft.world.item.Items.EMERALD_BLOCK
                    :net.minecraft.world.item.Items.EMERALD))return false;
        }
        return true;
    }
    /** Called after authoritative world restoration succeeds. */
    public void consumeRequired(ServerPlayer player){
        for(int i=0;i<9;i++)deposits.removeItem(i,1);
        returnItems(player);
    }
    private void returnItems(Player player){
        if(drained)return;
        drained=true;
        for(int i=0;i<9;i++){
            ItemStack stack=deposits.removeItemNoUpdate(i);
            if(stack.isEmpty())continue;
            // add() may partially insert and mutate its argument; drop only remainder.
            ItemStack remaining=stack.copy();
            player.getInventory().add(remaining);
            if(!remaining.isEmpty()){
                ServerLevel current=(ServerLevel)player.level();
                net.minecraft.world.entity.item.ItemEntity drop=new net.minecraft.world.entity.item.ItemEntity(
                        current,player.getX(),player.getY()+0.5,player.getZ(),remaining);
                drop.setDefaultPickUpDelay();
                current.addFreshEntity(drop);
            }
        }
        deposits.setChanged();
    }
    @Override public void removed(Player player){
        returnItems(player);
        super.removed(player);
    }
    @Override public boolean stillValid(Player player){
        if(!(player instanceof ServerPlayer p))return false;
        if(!p.getUUID().equals(owner)||p.level()!=level)return false;
        return RecoveryBeaconManager.canInteract(level,beacon,owner);
    }
}
