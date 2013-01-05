package xie.mods.xm.util;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
 
public class Droppables {
	private static Random rand = new Random();
	protected List<Drop> droppables;
	
	public Droppables() {
		droppables=new LinkedList<Drop>();
	}
	
	public Droppables(Drop drop) {
		this();
		droppables.add(drop);
	}
	
	public Droppables(Collection<Drop> dropCollection) {
		droppables=new LinkedList<Drop>(dropCollection);
	}
	
	public void add(Drop drop) {
		droppables.add(drop);
	}
	
	// Be careful, this method's CPU cycle cost may not scale well when drop number -> large
	public List<ItemStack> getDrops() {
		return getDropsForMetaAndMultiplier(-1, 0);
	}
	
	public List<ItemStack> getDropsForMetaAndMultiplier(int meta, int multiplier) {
		if (droppables==null) return null;
		// TODO something screwy in here wrt drops when chance=100%
		List<ItemStack> drops = new LinkedList<ItemStack>();
		for (Drop d : droppables) {
			for (int i=0; i<d.getQuantity(); i++) {
				ItemStack stack = d.getDropForMeta(rand, meta);
				if (stack!=null) {
					stack.stackSize=1;
					if (multiplier>0) {
						// apply multiplier
						int n = rand.nextInt(multiplier + 2) - 1;
						if(n > 0) stack.stackSize*=(n+1);
					}
					drops.add(stack);
				}
			}
		}
		return drops;
	}
	
	// wrapper, chances are taken care of internally, so parameter f is ignored
	public void dropBlockAsItemWithChance(World world, int i, int j, int k, int l, float f, int multiplier) {
        dropBlockAsItem(world,i,j,k,l,multiplier);
	}
	
	public void dropBlockAsItem(World world, int i, int j, int k, int l, int multiplier) {
		if(world.isRemote) return;

        // spawn drops
		Xie.spawnItems(getDropsForMetaAndMultiplier(l, multiplier), world, i, j, k);
	}
	
	public void dropBlockAsItem(World world, int i, int j, int k, int l, int multiplier, int saturation) {
		if(world.isRemote) return;
		List<ItemStack> drops = getDropsForMetaAndMultiplier(l,multiplier);
		if (drops==null) return;
		
		if (Xie.entityCount(world,i,j,k,drops)>saturation) return;
		
        // spawn drops
		dropBlockAsItem(world,i,j,k,l,multiplier);
	}
	
	public int size() {
		return droppables.size();
	}
	
	public Boolean isEmpty() {
		return droppables.isEmpty();
	}
	
	@Override
	public String toString() {
		String rtn = "[XieDroppable] ("+size()+")\n";
		for (Drop d : droppables) rtn+="[Drop] "+d.toString()+"\n";
		
		return rtn;
	}
	
	// TODO support remove
	// TODO support OR drop type in addition to AND
	// TODO support changing chance for existing drop
}
