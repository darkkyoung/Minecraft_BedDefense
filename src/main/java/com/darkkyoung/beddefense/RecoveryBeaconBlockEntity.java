package com.darkkyoung.beddefense;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Render-only beacon beam. Deliberately has NO vanilla BeaconBlockEntity ticker,
 * base/pyramid check, inventory, primary/secondary powers, or effects.
 */
public final class RecoveryBeaconBlockEntity extends BlockEntity implements BeaconBeamOwner {
    private static final List<BeaconBeamOwner.Section> GREEN_BEAM=
            List.of(new BeaconBeamOwner.Section(0xA8FF73));
    public RecoveryBeaconBlockEntity(BlockPos pos,BlockState state){
        super(RecoveryBeaconRegistry.ENTITY_TYPE,pos,state);
    }
    @Override public List<BeaconBeamOwner.Section> getBeamSections(){return GREEN_BEAM;}
}
