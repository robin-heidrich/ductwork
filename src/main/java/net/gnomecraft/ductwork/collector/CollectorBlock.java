package net.gnomecraft.ductwork.collector;

import net.gnomecraft.ductwork.Ductwork;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import java.util.ArrayList;
import java.util.List;

public class CollectorBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = FacingBlock.FACING;
    public static final BooleanProperty ENABLED = BooleanProperty.of("enabled");
    private static final VoxelShape COLLECTOR_SHAPE_NORTH = VoxelShapes.union(
            Block.createCuboidShape(0.0D, 0.0D, 12.0D, 16.0D, 16.0D, 16.0D),
            Block.createCuboidShape(4.0D, 4.0D, 11.0D, 12.0D, 12.0D, 12.0D),
            Block.createCuboidShape(5.0D, 5.0D,  0.0D, 11.0D, 11.0D, 11.0D)
    );
    private static final VoxelShape COLLECTOR_SHAPE_EAST = VoxelShapes.union(
            Block.createCuboidShape(0.0D, 0.0D, 0.0D,  4.0D, 16.0D, 16.0D),
            Block.createCuboidShape(4.0D, 4.0D, 4.0D,  5.0D, 12.0D, 12.0D),
            Block.createCuboidShape(5.0D, 5.0D, 5.0D, 16.0D, 11.0D, 11.0D)
    );
    private static final VoxelShape COLLECTOR_SHAPE_SOUTH = VoxelShapes.union(
            Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 16.0D,  4.0D),
            Block.createCuboidShape(4.0D, 4.0D, 4.0D, 12.0D, 12.0D,  5.0D),
            Block.createCuboidShape(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 16.0D)
    );
    private static final VoxelShape COLLECTOR_SHAPE_WEST = VoxelShapes.union(
            Block.createCuboidShape(12.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D),
            Block.createCuboidShape(11.0D, 4.0D, 4.0D, 12.0D, 12.0D, 12.0D),
            Block.createCuboidShape( 0.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D)
    );
    private static final VoxelShape COLLECTOR_SHAPE_DOWN = VoxelShapes.union(
            Block.createCuboidShape(0.0D, 12.0D, 0.0D, 16.0D, 16.0D, 16.0D),
            Block.createCuboidShape(4.0D, 11.0D, 4.0D, 12.0D, 12.0D, 12.0D),
            Block.createCuboidShape(5.0D,  0.0D, 5.0D, 11.0D, 11.0D, 11.0D)
    );
    private static final VoxelShape COLLECTOR_SHAPE_UP = VoxelShapes.union(
            Block.createCuboidShape(0.0D, 0.0D, 0.0D, 16.0D,  4.0D, 16.0D),
            Block.createCuboidShape(4.0D, 4.0D, 4.0D, 12.0D,  5.0D, 12.0D),
            Block.createCuboidShape(5.0D, 5.0D, 5.0D, 11.0D, 16.0D, 11.0D)
    );

    public CollectorBlock(Settings settings) {
        super(settings);

        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.DOWN)
                .with(ENABLED,true)
        );
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CollectorEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return checkType(type, Ductwork.COLLECTOR_ENTITY, CollectorEntity::tick);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            if (player.isSneaking()) {
                // Sneak + Use = toggle ENABLED
                // flags == 0x4 means notify neighbors in server only
                world.setBlockState(pos, state.with(ENABLED, !state.get(ENABLED)), 4);
            } else {
                // just Use = open GUI
                this.openContainer(world, pos, player);
            }
        }

        return ActionResult.SUCCESS;
    }

    private void openContainer(World world, BlockPos blockPos, PlayerEntity playerEntity) {
        BlockEntity blockEntity = world.getBlockEntity(blockPos);

        if (blockEntity instanceof CollectorEntity) {
            playerEntity.openHandledScreen((NamedScreenHandlerFactory) blockEntity);
        }
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState blockState, LootContext.Builder lootContext$Builder) {
        ArrayList<ItemStack> dropList = new ArrayList<ItemStack>();
        dropList.add(new ItemStack(this));
        return dropList;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getSide().getOpposite());
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (world != null && !oldState.isOf(state.getBlock())) {
            this.updateEnabled(world, pos, state);
        }
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block block, BlockPos fromPos, boolean notify) {
        if (world != null) {
            this.updateEnabled(world, pos, state);
        }
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighbor, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (world instanceof World) {
            state = state.with(ENABLED, !((World) world).isReceivingRedstonePower(pos));
        }

        return super.getStateForNeighborUpdate(state, direction, neighbor, world, pos, neighborPos);
    }

    private void updateEnabled(World world, BlockPos pos, BlockState state) {
        boolean enabled = !world.isReceivingRedstonePower(pos);
        if (enabled != state.get(ENABLED)) {
            // flags == 0x4 means notify neighbors in server only
            world.setBlockState(pos, state.with(ENABLED, enabled), 4);
        }
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        if (itemStack.hasCustomName()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);

            if (blockEntity instanceof CollectorEntity) {
                ((CollectorEntity)blockEntity).setCustomName(itemStack.getName());
            }
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);

            if (blockEntity instanceof CollectorEntity) {
                ItemScatterer.spawn(world, pos, (CollectorEntity)blockEntity);
                world.updateComparators(pos,this);
            }

            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return ScreenHandler.calculateComparatorOutput(world.getBlockEntity(pos));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING).add(ENABLED);
    }

    @Override
    public boolean canPathfindThrough(BlockState state, BlockView world, BlockPos pos, NavigationType type) {
        return false;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {

        return switch (state.get(FACING)) {
            case NORTH -> COLLECTOR_SHAPE_NORTH;
            case EAST  -> COLLECTOR_SHAPE_EAST;
            case SOUTH -> COLLECTOR_SHAPE_SOUTH;
            case WEST  -> COLLECTOR_SHAPE_WEST;
            case DOWN  -> COLLECTOR_SHAPE_DOWN;
            case UP    -> COLLECTOR_SHAPE_UP;
        };
    }
}