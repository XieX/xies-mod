package mod.xie.xm.util;

import java.util.LinkedList;

import mod.xie.xm.file.XieLog;
import net.minecraft.block.Block;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.List;


import net.minecraft.src.ModLoader;
 
public class Recipe {
	//public static List<XieRecipe> recipeList = new LinkedList<XieRecipe>();
	
	protected boolean shapeless;
	protected String pattern[];
	protected char symbols[]; // symbols and contents need to be same length
	protected List<ItemStack> ingredients; // ingredients and symbols need to be same length
	protected ItemWrapper yield;
	protected String failMsg = "unknown error";
	
	public Recipe(ItemWrapper product, List<ItemStack> contents) {
		shapeless=true;
		yield = product;
		ingredients = contents;
		validate();
	}
	
	public Recipe(ItemWrapper product, List<ItemStack> contents, String patternString) {
		shapeless=false;
		yield = product;
		ingredients = contents;
		pattern = parsePattern(patternString);
		validate();
	}
	
	public void addRecipeUsingModLoader() {
		if (!validate()) return;
		if (shapeless) ModLoader.addShapelessRecipe(yield.getItemStack(), getIngredients());
		else ModLoader.addRecipe(yield.getItemStack(), getIngredients());
	}
	
	protected void setFailMsg(String s) { failMsg = s; }
	public String getFailMsg() { return failMsg; }
	
	protected static ItemStack[] parseIngredients(String string) {
		List<ItemStack> ingreds = Xie.parseItemStacks(string);
		if (ingreds.size()!=string.split(",").length) {
			return null;
		}
		
		if (ingreds.isEmpty()) return null;
		else {
			ItemStack r[] = new ItemStack[ingreds.size()];
			int n = 0;
			for (ItemStack i : ingreds) r[n++]=i;
			return r;
		}
	}
	
	protected String[] parsePattern(String string) {
		String rows[] = string.split(",");
		
		// validation
		if (rows.length>3) {
			// TODO throw an error
			return null;
		}
		for (int i=0; i<rows.length; i++) {
			if (rows[i].length()>3) {
				// TODO throw an error
				return null;
			}
		}
		
		// populate symbols array while we're at it
		List<Character> symbolz = new LinkedList<Character>();
		for (int i=0; i<string.length(); i++) {
			char c = string.charAt(i);
			if (c==' ' || c==',') continue;
			if (!symbolz.contains(c)) symbolz.add(c);
		}
		symbols = new char[symbolz.size()];
		for (int i=0; i<symbolz.size(); i++) symbols[i]=symbolz.get(i); 
		
		return rows;
	}
	
	protected Object[] getIngredients() {
		if (shapeless) {
			if (ingredients==null) {
				// TODO throw some kind of error I suppose... :P
				return null;
			}
			return ingredients.toArray();
		} else {
			if (ingredients==null || pattern==null || symbols==null) {
				// TODO throw some kind of error?
				return null;
			}
			int a = pattern.length;
			int b = symbols.length; // also b = ingredients.length;
			Object obj[] = new Object[a+b+b];
			for (int i=0; i<a; i++) {
				obj[i]=pattern[i];
			}
			for (int i=0; i<b; i++) {
				obj[a+2*i]=symbols[i];
				obj[a+2*i+1]=ingredients.get(i);
			}
			return obj;
		}
	}

	public Boolean validate() {
		Boolean flag=true;
		if (yield==null) {
			setFailMsg("failed to identify yield");
			flag=false;
		}
		if (ingredients==null) {
			setFailMsg("failed to identify ingredients");
			flag=false;
		}
		if (!shapeless) {
			if (symbols==null) {
				setFailMsg("failed to identify symbols");
				flag=false;
			}
			
			if (pattern==null) {
				setFailMsg("failed to identify pattern");
				flag=false;
			}
			
			if (symbols!=null && ingredients!=null)
			if (symbols.length!=ingredients.size()) {
				setFailMsg("length mismatch: found "+symbols.length+" symbols and "+ingredients.size()+" ingredients");
				flag=false;
			}
			if (pattern!=null)
			if (pattern.length>3) {
				setFailMsg("too many pattern rows");
				flag=false;
			}
			if (pattern!=null)
			for (int i=0; i<pattern.length; i++) if (pattern[i].length()>3) {
				setFailMsg("pattern row "+i+" too long");
				flag=false;
			}
		}
		return flag;
	}
	
	@Override
	public String toString() {
		String s="Recipe: ";
		
		if (yield!=null) s+="("+yield+")";
		else s+="(null):";
		
		Object o[] = getIngredients();
		if (o!=null) {
			for (Object ob : o) s+=o+" | ";
		}
		
		return s;
	}
	
	public boolean equals(Recipe other) {
		if (shapeless!=other.shapeless) return false;
		if (!shapeless) {
			if (pattern.length!=other.pattern.length) return false;
			for (int i=0; i<pattern.length; i++) 
				if (!pattern[i].equals(other.pattern[i])) return false;
			
			if (symbols.length!=other.symbols.length) return false;
			
			// compare ingredients, order matters
			if (ingredients.size()!=other.ingredients.size()) return false;
			for (int i=0; i<ingredients.size(); i++) 
				if (!Xie.itemstacksEqual(ingredients.get(i),other.ingredients.get(i))) return false;
		} else {
			// compare ingredients, but they may be in any order
			if (ingredients.size()!=other.ingredients.size()) return false;
			for (int i=0; i<ingredients.size(); i++) {
				boolean foundMatch = false;
				for (int j=0; j<ingredients.size() && !foundMatch; j++) {
					foundMatch = Xie.itemstacksEqual(ingredients.get(i), other.ingredients.get(j));
				}
				if (!foundMatch) return false;
			}
		}
		
		return yield.equals(other);
	}
	
	public static void makeArmorRecipe(int type, ItemWrapper product, ItemStack material) {
		String pattern;
		switch(type) {
		case 0: // helmet
			pattern = "XXX,X X";
			break;
		case 1: // chestplate
			pattern = "X X,XXX,XXX";
			break;
		case 2: // leggings
			pattern = "XXX,X X,X X";
			break;
		case 3: // boots
		default:
			pattern = "X X,X X";
		}
		
		List<ItemStack> armorMaterials = new LinkedList<ItemStack>();
		armorMaterials.add(material);
		new Recipe(product,armorMaterials,pattern).addRecipeUsingModLoader();
		// TODO include some error checking
	}
}
