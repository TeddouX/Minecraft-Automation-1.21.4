package teddy.minecraftautomation.blocks.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import teddy.minecraftautomation.blocks.AbstractPipeBlock;
import teddy.minecraftautomation.blocks.FluidPipeBlock;
import teddy.minecraftautomation.blocks.FluidPumpBlock;
import teddy.minecraftautomation.utils.ContainerUtils;

public class FluidPipeBlockEntity extends BlockEntityWithFluidStorage {
    private int maxPressure;
    private int flowPerTick;
    private int transferCooldown;
    public int cooldown = 0;
    public int directionIndex = 0;
    public int pressure = 0;

    public FluidPipeBlockEntity(BlockPos blockPos, BlockState blockState, int maxPressure, int flowPerTick, int maxFluidCapacityMb, int transferCooldown) {
        super(ModBlockEntities.FLUID_PIPE_BE, blockPos, blockState, maxFluidCapacityMb);

        this.maxPressure = maxPressure;
        this.flowPerTick = flowPerTick;
        this.transferCooldown = transferCooldown;
    }

    public static void tick(Level level, BlockPos blockPos, BlockState state, FluidPipeBlockEntity fluidPipeBlockEntity) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel))
            return;

        // Update pressure and clamp it to the maxPressure
        fluidPipeBlockEntity.pressure = Math.min(getPressureAmountForBlock(level, state, blockPos), fluidPipeBlockEntity.maxPressure);

        if (fluidPipeBlockEntity.pressure <= 0) return;

        // Only transfer items when the cooldown reaches 0
        fluidPipeBlockEntity.cooldown++;
        if (fluidPipeBlockEntity.cooldown >= fluidPipeBlockEntity.transferCooldown)
            fluidPipeBlockEntity.cooldown = 0;
        else
            return;

        Direction[] directions = Direction.values();
        for (int i = 0; i < directions.length; i++) {
            // Used so that it doesn't check the same direction two times in a row if it has other connections
            fluidPipeBlockEntity.directionIndex++;
            fluidPipeBlockEntity.directionIndex %= directions.length;

            boolean success = ContainerUtils.FluidContainer.handleDirection(
                    directions[fluidPipeBlockEntity.directionIndex],
                    serverLevel,
                    blockPos,
                    state,
                    fluidPipeBlockEntity,
                    ContainerUtils.Flow.OUTGOING,
                    fluidPipeBlockEntity.flowPerTick);

            if (success)
                break;
        }
    }

    static int getPressureAmountForBlock(Level level, BlockState state, BlockPos pos) {
        Direction[] directions = Direction.values();
        BlockEntity blockEntity = level.getBlockEntity(pos);

        // Sanity checks
        if (!(state.getBlock() instanceof FluidPipeBlock) || !(blockEntity instanceof FluidPipeBlockEntity fluidPipeBlockEntity))
            return 0;

        int maxPressure = 0;
        for (Direction dir : directions) {
            BlockState relativeBlockState = level.getBlockState(pos.relative(dir));
            BlockEntity relativeBlockEntity = level.getBlockEntity(pos.relative(dir));

            // If the other block is an item pump and the pipe is connected to its output
            if (relativeBlockState.getBlock() instanceof FluidPumpBlock fluidPumpBlock
                    && state.getValue(AbstractPipeBlock.getFacingPropertyFromDirection(dir))
                    && fluidPumpBlock.getOutputDirections(relativeBlockState).contains(dir)) {

                if (!(relativeBlockEntity instanceof FluidPumpBlockEntity fluidPumpBlockEntity))
                    continue;

                maxPressure = Math.max(fluidPumpBlockEntity.inducedPressure, maxPressure);
            } else if (relativeBlockState.getBlock() instanceof FluidPipeBlock) {
                if (!(relativeBlockEntity instanceof FluidPipeBlockEntity relativeFluidPipeBlockEntity))
                    continue;

                maxPressure = Math.max(relativeFluidPipeBlockEntity.pressure - 1, maxPressure);
            }
        }

        return Math.min(fluidPipeBlockEntity.maxPressure, maxPressure);
    }

    @Override
    public void setChanged() {
        super.setChanged();

        if (this.getLevel() != null)
            this.getLevel().sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();

        this.saveAdditional(nbt, provider);

        return nbt;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        nbt.putInt("maxPressure", maxPressure);
        nbt.putInt("flowPerTick", flowPerTick);
        nbt.putInt("cooldown", cooldown);
        nbt.putInt("transferCooldown", transferCooldown);
        nbt.putInt("directionIndex", directionIndex);
        nbt.putInt("pressure", pressure);

        super.saveAdditional(nbt, provider);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider provider) {
        super.loadAdditional(nbt, provider);

        this.maxPressure = nbt.getInt("maxPressure");
        this.flowPerTick = nbt.getInt("flowPerTick");
        this.cooldown = nbt.getInt("cooldown");
        this.transferCooldown = nbt.getInt("transferCooldown");
        this.directionIndex = nbt.getInt("directionIndex");
        this.pressure = nbt.getInt("pressure");
    }
}