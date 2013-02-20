package mod.xie.xm.parser;

import java.util.LinkedList;

import mod.xie.xm.util.ItemWrapper;
import mod.xie.xm.util.JobType;
import mod.xie.xm.util.Recipe;
import mod.xie.xm.util.Xie;
import net.minecraft.block.Block;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;

import java.util.List;
import java.util.Map;



import argo.jdom.JsonNode;
import argo.jdom.JsonNodeType;

public class JobRecipe extends Job  {
	
	protected Recipe recipe;

	public JobRecipe(String n, JsonNode node) {
		super(JobType.RECIPE, n, node);
	}

	@Override
	public int doWork() {
		logOpen();
		// first check to see if its a cull recipe
		if (name.equals("cull")) return doCulling();
		if (isKey("cull")) return doCulling();
		
		// identify product
		String p = name;
		if (isKey("product")) p = getStr("product");
		ItemWrapper product = Xie.parseItemWrapper(p);
		if (product==null) {
			logDelayed("Failed to identify product "+p);
			logClose();
			return 0;
		}
		
		// check for a smelting recipe
		if (isKey("smelt")) return doSmelting(product);
		
		// search for content
		String ingredients = null;
		if (!isObject(jNode)) ingredients = jNode.getText();
		else if (isKey("content")) ingredients = getStr("content");
		else {
			logFailure("Failed to find ingredients for this recipe. Job undoable.");
			return 0;
		}
		
		if (ingredients.contains("$")) {
			List<Recipe> recipes = doComplexRecipe(product, ingredients);
			if (recipes==null) {
				logClose();
				return 0;
			}
			
			for (Recipe recipe : recipes) {
				if (recipe.validate()) recipe.addRecipeUsingModLoader();
				else {
					logDelayed("Recipe failed: "+recipe);
					logClose();
					return 0;
				}
			}
			
			if (recipes.isEmpty()) {
				logDelayed("Complex Recipe yielded nothing");
				logClose();
				return 0;
			}
			
			logInfo("Complex Recipe Resolved, added "+recipes.size()+" recipes");
			isDone=true;
			return recipes.size();
		}
		
		List<ItemStack> contents = Xie.parseItemStacks(ingredients);
		if (contents==null) {
			logDelayed("Recipe failed, could not identify one or more ingredients from: "+ingredients);
			logClose();
			return 0;
		}
		
		if (isKey("pattern")) {
			// shaped recipe
			String pattern = getStr("pattern");
			logFiner("Attempting shaped recipe ("+product+","+ingredients+","+pattern+")");
			if (ingredients.contains("whiskey")) {
				int wtf = 0;
			}
			
			recipe = new Recipe(product, contents, pattern);
		} else {
			// shapeless recipe
			logFiner("Attempting shapeless recipe ("+product+","+ingredients+")");
			recipe = new Recipe(product, contents);
		}
		
		if (recipe!=null && recipe.validate()) {
			isDone=true;
			recipe.addRecipeUsingModLoader();
			logInfo(" === Recipe done! === ");
		} else {
			logDelayed("Recipe failed, "+recipe.getFailMsg());
			logClose();
			return 0;
		}
		
		return 1;
	}
	
	// process a recipe containing one or more aliases
	protected List<Recipe> doComplexRecipe(ItemWrapper product, String ingreds) {
		String pattern = null;
		if (isKey("pattern")) pattern = getStr("pattern");
		
		// expand ingredient list
		List<ItemStack> ingredients[] = new LinkedList[9];
		int index = 0;
		String ingredNames[] = ingreds.split(",");
		for (String s : ingredNames) {
			if (s.contains("$")) {
				if (s.length()==1) {
					logFailure("Invalid alias "+s+", recipe failed");
					return null;
				}
				// expand alias
				String aliasName = s.substring(1);
				if (!Xie.aliasMap.containsKey(aliasName)) {
					logDelayed("Couldn't identify alias "+s);
					return null;
				} else {
					ingredients[index++]=ItemWrapper.toItemStackList(Xie.aliasMap.get(aliasName));
				}
			} else {
				// add single ingredient to list
				ItemStack is = Xie.parseItemStack(s);
				if (is==null) {
					logDelayed("Did not recognise ingredient "+s);
					return null;
				}
				List<ItemStack> l = new LinkedList<ItemStack>();
				l.add(is);
				ingredients[index++]=l;
			}
		}
		// flesh out remainder of ingredients lists with empty lists
		while (index<ingredients.length) ingredients[index++]=new LinkedList();
		
		// array containing true or false for if ingredient is a list or a single item
		boolean isList[] = new boolean[9];
		for (int i=0; i<9; i++) {
			if (ingredients[i].isEmpty() || ingredients[i].size()<2) isList[i]=false; 
			else isList[i]=true;
		}
		
		// create a recipe for each combination of ingredients
		List<Recipe> recipes = new LinkedList<Recipe>();
		for (ItemStack a : ingredients[0]) {
			if (ingredients[1].isEmpty()) processRecipe(recipes, product, new ItemStack[] {a}, isList, pattern);
			else for (ItemStack b : ingredients[1]) {
				if (ingredients[2].isEmpty()) processRecipe(recipes, product, new ItemStack[] {a,b}, isList, pattern);
				else for (ItemStack c : ingredients[2]) {
					if (ingredients[3].isEmpty()) processRecipe(recipes, product, new ItemStack[] {a,b,c}, isList, pattern);
					else for (ItemStack d : ingredients[3]) {
						if (ingredients[4].isEmpty()) processRecipe(recipes, product, new ItemStack[] {a,b,c,d}, isList, pattern);
						else for (ItemStack e : ingredients[4]) {
							if (ingredients[5].isEmpty()) processRecipe(recipes, product, new ItemStack[] {a,b,c,d,e}, isList, pattern);
							else for (ItemStack f : ingredients[5]) {
								if (ingredients[6].isEmpty()) processRecipe(recipes, product, new ItemStack[] {a,b,c,d,e,f}, isList, pattern);
								else for (ItemStack g : ingredients[6]) {
									if (ingredients[7].isEmpty()) processRecipe(recipes, product, new ItemStack[] {a,b,c,d,e,f,g}, isList, pattern);
									else for (ItemStack h : ingredients[7]) {
										if (ingredients[8].isEmpty()) processRecipe(recipes, product, new ItemStack[] {a,b,c,d,e,f,g,h}, isList, pattern);
										else for (ItemStack i : ingredients[8]) {
											processRecipe(recipes, product, new ItemStack[] {a,b,c,d,e,f,g,h,i}, isList, pattern);
										}
									}
								}
							}
						}
					}
				}
			}
		}
		
		return recipes;
	}
	
	protected void processRecipe(List<Recipe> recipeList, ItemWrapper product, ItemStack items[], boolean isList[], String pattern) {
		List<ItemStack> these = new LinkedList<ItemStack>();
		for (ItemStack i : items) these.add(i);
		
		// check to see if there are doubled up ingredients
		if (ingredientsMatch(these, isList)) return;	
		
		// create recipe object
		Recipe recipe;
		if (pattern==null) recipe = new Recipe(product, these);
		else recipe = new Recipe(product, these, pattern);
		
		// check to make sure the same recipe has not already been added
		for (Recipe r : recipeList) if (r.equals(recipe)) return;
		
		recipeList.add(recipe);
	}
	
	protected boolean ingredientsMatch(List<ItemStack> ingredients, boolean careAbout[]) {
		for (int i=0; i<ingredients.size(); i++) {
			if (i>careAbout.length || !careAbout[i]) continue;
			for (int j=0; j<ingredients.size(); j++) {
				if (i==j) continue;
				if (ingredients.get(i).itemID == ingredients.get(j).itemID && 
					ingredients.get(i).getItemDamage() == ingredients.get(j).getItemDamage()) 
					return true;
			}
		}
		return false;
	}
	
	protected int doCulling() {
		logFiner("Processing culling recipe");
		
		ItemStack is = null;
		
		if (jNode.getType()==JsonNodeType.STRING) {
			is = Xie.parseItemStack(jNode.getText());	
		} else if (jNode.getType()==JsonNodeType.OBJECT) {
			is = Xie.parseItemStack(getStr("cull"));
		} else {
			logFailure("Invalid culling recipe");
			return 0;
		}
		
		if (is==null) {
			try {
				logDelayed("Failed to parse product whose recipe(s) is(are) to be culled - "+jNode.getText());
			} catch (IllegalStateException e) {
				logDelayed("Failed to parse product whose recipe(s) is(are) to be culled.");
			}
			logClose();
			return 0;
		}
		
		List recipes = CraftingManager.getInstance().getRecipeList();
		List cullVictims = new LinkedList();
		for (Object o : recipes) {
			if (o instanceof IRecipe) {
				IRecipe r = (IRecipe)o;
				if (Xie.itemstacksEqual(is,r.getRecipeOutput())) {
					logFiner("Culling recipe for "+is.getItem().getItemName()+":"+is.stackSize+":"+is.getItemDamage());
					cullVictims.add(o);
				}
			}
		}
		if (cullVictims.isEmpty()) logIncomplete("Failed to find any matching recipes, nothing culled");
		else recipes.removeAll(cullVictims);
		
		isDone=true;
		return 0;
	}
	
	protected int doSmelting(ItemWrapper product) {
		logFiner("Processing smelting recipe for "+product.getItemName());
		
		String smelt = getStr("smelt");
		int smeltable = Xie.parseItemID(smelt);
		if (smeltable<0) {
			logDelayed("Failed to identify smeltable "+smelt);
			logClose();
			return 0;
		}
		
		logFiner("Adding smelting recipe "+smelt+" -> "+product.toString());
		
		Xie.addSmeltingRecipe(smeltable, product);
		if (product.getItem() instanceof ItemFood) {
			foodSmeltsSoFar.put(new ItemWrapper(smeltable),product);
			for (Map<ItemWrapper,ItemWrapper> map : foodSmeltMapsToUpdate) map.put(new ItemWrapper(smeltable),product);
		}
		
		isDone=true;
		return 1;
	}
	
}
