package xie.mods.xm.util;

import java.util.LinkedList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.CraftingManager;

public class GameState {

	protected Block[] blocksList;
	protected Item[] itemsList;
	protected List recipes;
	
	public GameState() {
		blocksList = new Block[Block.blocksList.length];
		for (int i=0; i<Block.blocksList.length; blocksList[i]=Block.blocksList[i++]);
		
		itemsList = new Item[Item.itemsList.length];
		for (int i=0; i<Item.itemsList.length; itemsList[i]=Item.itemsList[i++]);
		
		recipes = new LinkedList();
		recipes.addAll(CraftingManager.getInstance().getRecipeList());
	}
	
	public void restore() {
		for (int i=0; i<Block.blocksList.length; i++) {
			Block.blocksList[i]=blocksList[i];
		}
		
		for (int i=0; i<Item.itemsList.length; i++) {
			Item.itemsList[i]=itemsList[i];
		}
		
		List recipeList = CraftingManager.getInstance().getRecipeList();
		recipeList.clear();
		recipeList.addAll(recipes);
	}
	
}
