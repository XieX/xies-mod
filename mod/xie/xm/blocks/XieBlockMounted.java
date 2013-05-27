package mod.xie.xm.blocks;


import mod.xie.xm.parser.Location;
import mod.xie.xm.tex.AutomagicTextureFactory;
import mod.xie.xm.tex.XieTex;
import mod.xie.xm.util.Xie;
import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import net.minecraft.block.StepSound;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.creativetab.CreativeTabs;

// TODO FTFESAD this is so broken

public class XieBlockMounted extends XieBlock{

	public boolean isMovable = true;
	public ItemStack movableTool = null;
	
	protected boolean canFace[];
	protected float blockBoundsBase [];
	
	public XieBlockMounted(String n, int id, Material mat) {
		super(id, mat, n);
	}
	
	public void setCanFace(int i, boolean b) {
		if (canFace==null) canFace = new boolean[6];
		if (i<0 || i>=6) return;
		canFace[i]=b;
	}
	
	public void setCanFace(String face, boolean b) {
		int i = -1;
		for (Location l : Location.values()) {
			if (l.name().toLowerCase().equals(face.toLowerCase())) i=l.ordinal();
		}
		setCanFace(i,b);
	}
	
	public void setCanFace(String facing) {
		String faces[] = facing.split(",");
		for (String f : faces) setCanFace(f,true);
	}

	public boolean canPlaceBlockAt(World world, int i, int j, int k) {
		return super.canPlaceBlockAt(world, i, j, k);
    }
	
	public boolean canPlaceBlockOnSide(World world, int i, int j, int k, int l) {
		if (canFace==null) return canPlaceBlockAt(world, i, j, k);
		if (canFace[l]) return canPlaceBlockAt(world, i, j, k);
		else return false;
	}
		
	@Override
	public void onBlockPlacedBy(World world, int i, int j, int k, EntityLiving e, ItemStack blockItem) {
		int facing = Xie.getFacing(e);
        Xie.setBlockAndMeta(world, i, j, k, blockID, facing);
        super.onBlockPlacedBy(world, i, j, k, e, blockItem);
    }
	
	public void onNeighborBlockChange(World world, int i, int j, int k, int l) {
		// TODO
    }
	
	public void dropBlock(World world, int i, int j, int k) {
		dropBlockAsItem(world, i, j, k, world.getBlockMetadata(i, j, k), 0);
        Xie.setBlock(world, i, j, k, 0);
	}
	
    @Override
	public boolean isOpaqueCube() { return false; }

    @Override
	public boolean renderAsNormalBlock() { return false; }
	
	protected boolean checkCeiling(World world, int i, int j, int k) { return world.getBlockId(i, j+1, k)!=0; }
	protected boolean checkFloor(World world, int i, int j, int k) { return world.getBlockId(i, j-1, k)!=0;	}
	protected boolean checkWest(World world, int i, int j, int k) {	return world.getBlockId(i, j, k-1)!=0;	}
	protected boolean checkEast(World world, int i, int j, int k) {	return world.getBlockId(i, j, k+1)!=0; }
	protected boolean checkNorth(World world, int i, int j, int k) { return world.getBlockId(i-1, j, k)!=0;	}
	protected boolean checkSouth(World world, int i, int j, int k) { return world.getBlockId(i+1, j, k)!=0;	}

	public boolean blockActivated(World world, int i, int j, int k, EntityPlayer entityplayer) {
    	if (!isMovable) return false;
    	if (entityplayer.getCurrentEquippedItem()!=movableTool) return false;
    	
    	int meta = world.getBlockMetadata(i, j, k);
    	
    	for (int n=1; n<6; n++) {
    		int newMeta = (meta + n) % 6;
    		if (canFace==null || canFace[newMeta]) {
    			meta = newMeta;
    			break;
    		}
    	}
    	
    	Xie.setBlockMeta(world, i, j, k, meta);
    	return true;
    }
	
	@Override
	public void registerIcons(IconRegister registrar) {
		tex = AutomagicTextureFactory.getBlock(registrar, getUnlocalizedName2());
	}
}
