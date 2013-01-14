package mod.xie.xm.util;

import mod.xie.xm.blocks.IXieBlock;
import mod.xie.xm.blocks.XieBlockFurnace;
import mod.xie.xm.blocks.XieGuiFurnace;
import mod.xie.xm.blocks.XieTileEntityFurnace;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.world.World;
import cpw.mods.fml.common.network.IGuiHandler;

public class XieGuiHandler implements IGuiHandler {

	private static XieGuiHandler instance;
	public static XieGuiHandler get() { 
		if (instance==null) instance = new XieGuiHandler();
		return instance; 
	}
	
	private XieGuiHandler() {}
	
	// TODO dirty rotten hack
	@Override
	public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
		Block b = Block.blocksList[world.getBlockId(x, y, z)];
		
		if (b instanceof XieBlockFurnace) {
			// it's one of mine! Return the block's container
		    XieTileEntityFurnace furnace = (XieTileEntityFurnace)world.getBlockTileEntity(x, y, z);
		    if (furnace==null) return null;
			return new ContainerFurnace(player.inventory, furnace);
		} else return null;
	}

	// TODO dirty rotten hack
	@Override
	public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
		Block b = Block.blocksList[world.getBlockId(x, y, z)];
		
		if (b instanceof XieBlockFurnace) {
			// it's one of mine! Make it open a gui...
		    XieTileEntityFurnace furnace = (XieTileEntityFurnace)world.getBlockTileEntity(x, y, z);
		    if (furnace==null) return null;
			return new XieGuiFurnace(player.inventory, furnace);
		} else return null;
	}

}
