package mod.xie.xm.blocks;

import mod.xie.xm.util.Xie;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;

public class XieTileEntityChest extends TileEntityChest {
	
	public XieBlockChest chest;
	protected String blockIdentifier;
	
	protected int ticksSinceLastSync;
	
	public TileEntityChest adjacentChestYNeg;
    public TileEntityChest adjacentChestYPos;
    	
    public XieTileEntityChest() {
    	super();
    }
    
	public XieTileEntityChest(XieBlockChest chestBlock) {
		super();
		chest = chestBlock;
		blockIdentifier = chestBlock.getBlockName();
	}
	
    public String getInvName() { 
        validateBlockObject();
    	return chest.getDisplayName();
    }
    
    public void checkForAdjacentChests() {
        validateBlockObject();
        if (adjacentChestChecked || (!chest.mergeHorizontal && !chest.mergeVertical))
        	return;
        
        adjacentChestChecked = true;
        adjacentChestZNeg = null;
        adjacentChestXPos = null;
        adjacentChestXNeg = null;
        adjacentChestZPosition = null;
        adjacentChestYNeg = null;
        adjacentChestYPos = null;
        
        if (chest.mergeHorizontal) {
	        if (worldObj.getBlockId(xCoord - 1, yCoord, zCoord) == getChestBlockID())
	        	adjacentChestXNeg = (TileEntityChest)worldObj.getBlockTileEntity(xCoord - 1, yCoord, zCoord);
	        if (worldObj.getBlockId(xCoord + 1, yCoord, zCoord) == getChestBlockID())
	        	adjacentChestXPos = (TileEntityChest)worldObj.getBlockTileEntity(xCoord + 1, yCoord, zCoord);
	        if (worldObj.getBlockId(xCoord, yCoord, zCoord - 1) == getChestBlockID())
	        	adjacentChestZNeg = (TileEntityChest)worldObj.getBlockTileEntity(xCoord, yCoord, zCoord - 1);
	        if (worldObj.getBlockId(xCoord, yCoord, zCoord + 1) == getChestBlockID())
	        	adjacentChestZPosition = (TileEntityChest)worldObj.getBlockTileEntity(xCoord, yCoord, zCoord + 1);
        }
        
        if (chest.mergeVertical){ 
        	if (worldObj.getBlockId(xCoord, yCoord - 1, zCoord) == getChestBlockID())
        		adjacentChestYNeg = (TileEntityChest)worldObj.getBlockTileEntity(xCoord, yCoord - 1, zCoord);
        	if (worldObj.getBlockId(xCoord, yCoord + 1, zCoord) == getChestBlockID())
        		adjacentChestYPos = (TileEntityChest)worldObj.getBlockTileEntity(xCoord, yCoord + 1, zCoord);
        }
        
        if (adjacentChestZNeg != null) adjacentChestZNeg.updateContainingBlockInfo();
        if (adjacentChestZPosition != null) adjacentChestZPosition.updateContainingBlockInfo();
        if (adjacentChestXPos != null) adjacentChestXPos.updateContainingBlockInfo();
        if (adjacentChestXNeg != null) adjacentChestXNeg.updateContainingBlockInfo();
        if (adjacentChestZPosition != null) adjacentChestZPosition.updateContainingBlockInfo();
        if (adjacentChestZNeg != null) adjacentChestZNeg.updateContainingBlockInfo();
    }

    public void updateEntity() {
        validateBlockObject();        
        if (chest.mergeHorizontal || chest.mergeVertical) checkForAdjacentChests();
        
        if (chest.animate) applyChestAnimation();
    }
    
    protected void applyChestAnimation() {
        if ((++ticksSinceLastSync % 20) * 4 == 0) Xie.playSoundEffect(worldObj, xCoord, yCoord, zCoord, 1, numUsingPlayers);

        prevLidAngle = lidAngle;
        float f = 0.1F;
        if (numUsingPlayers > 0 && lidAngle == 0.0F && adjacentChestZNeg == null && adjacentChestXNeg == null)
        {
            double d = (double)xCoord + 0.5D;
            double d1 = (double)zCoord + 0.5D;
            if (adjacentChestZPosition != null) d1 += 0.5D;
            if (adjacentChestXPos != null) d += 0.5D;
            worldObj.playSoundEffect(d, (double)yCoord + 0.5D, d1, chest.getOpenSound(), 0.5F, worldObj.rand.nextFloat() * 0.1F + 0.9F);
        }
        if (numUsingPlayers == 0 && lidAngle > 0.0F || numUsingPlayers > 0 && lidAngle < 1.0F)
        {
            float f1 = lidAngle;
            if (numUsingPlayers > 0) lidAngle += f;
            else lidAngle -= f;
            if (lidAngle > 1.0F) lidAngle = 1.0F;
            float f2 = 0.5F;
            if (lidAngle < f2 && f1 >= f2 && adjacentChestZNeg == null && adjacentChestXNeg == null)
            {
                double d2 = (double)xCoord + 0.5D;
                double d3 = (double)zCoord + 0.5D;
                if (adjacentChestZPosition != null) d3 += 0.5D;
                if (adjacentChestXPos != null) d2 += 0.5D;
                worldObj.playSoundEffect(d2, (double)yCoord + 0.5D, d3, chest.getCloseSound(), 0.5F, worldObj.rand.nextFloat() * 0.1F + 0.9F);
            }
            if (lidAngle < 0.0F) lidAngle = 0.0F;
        }
    }
    
    protected int getChestBlockID() {
        validateBlockObject();
    	if (chest==null) return Block.chest.blockID;
    	else return chest.blockID;
    }
    
    // TODO This is rather lacking in robustness
    protected void validateBlockObject() {
    	if (chest!=null) return;
    	chest = (XieBlockChest)Xie.parseBlock(blockIdentifier);
    }
    
    public void readFromNBT(NBTTagCompound nbttagcompound)
    {
        super.readFromNBT(nbttagcompound);
        blockIdentifier = nbttagcompound.getString("BlockIdentifier");
        validateBlockObject();
    }

    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        nbttagcompound.setString("BlockIdentifier", blockIdentifier);
    }
}
