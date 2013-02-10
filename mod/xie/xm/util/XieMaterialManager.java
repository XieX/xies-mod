package mod.xie.xm.util;

import java.util.HashMap;
import java.util.Map;

import mod.xie.xm.file.XieLog;
import net.minecraft.item.EnumArmorMaterial;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.Item;
import net.minecraftforge.common.EnumHelper;

public class XieMaterialManager {
	
	private Map<String,EnumToolMaterial> toolMaterials;
	private Map<String,EnumArmorMaterial> armorMaterials;
	
	private static XieMaterialManager instance;
	private XieMaterialManager() {
		toolMaterials = new HashMap();
		armorMaterials = new HashMap();
	}
	
	public static XieMaterialManager instance() {
		if (instance == null) instance = new XieMaterialManager();
		return instance;
	}
	
	public EnumToolMaterial newToolMaterial(String name, int harvestLevel, int maxUses, float efficiency, int damage, int enchantability, Item craftingMaterial) {
		name = name.toUpperCase();
		EnumToolMaterial material = EnumHelper.addToolMaterial(name, harvestLevel, maxUses, efficiency, damage, enchantability);
		if (material == null) {
			XieLog.log().logIncomplete("Failure to add tool material "+name);
			return null;
		}
		material.customCraftingMaterial = craftingMaterial;
		
		toolMaterials.put(name,material);
		return material;
	}
	
	public EnumToolMaterial getToolMaterial(String name) {
		name = name.toUpperCase();
		if (toolMaterials.containsKey(name)) {
			return toolMaterials.get(name);
		}
		
		EnumToolMaterial allMaterials[] = EnumToolMaterial.values();
		for (EnumToolMaterial material : allMaterials) {
			if (material.name().toUpperCase().equals(name)) return material;
		}
		
		XieLog.log().logIncomplete("Couldn't satisfy request for tool material "+name+". Getting wood instead.");
		return EnumToolMaterial.WOOD;
	}
	
	public EnumArmorMaterial newArmorMaterial(String name, int durability, int[] reductionAmounts, int enchantability, Item craftingMaterial) {
		name = name.toUpperCase();
		EnumArmorMaterial material = EnumHelper.addArmorMaterial(name,durability,reductionAmounts,enchantability);
		if (material == null) {
			XieLog.log().logIncomplete("Failure to add armor material "+name);
			return null;
		}
		material.customCraftingMaterial = craftingMaterial;
		
		armorMaterials.put(name,material);
		return material;
	}
	
	public EnumArmorMaterial getArmorMaterial(String name) {
		name = name.toUpperCase();
		if (armorMaterials.containsKey(name)) {
			return armorMaterials.get(name);
		}
		
		EnumArmorMaterial allMaterials[] = EnumArmorMaterial.values();
		for (EnumArmorMaterial material : allMaterials) {
			if (material.name().toUpperCase().equals(name)) return material;
		}
		
		XieLog.log().logIncomplete("Couldn't satisfy request for armor material "+name+". Getting cloth instead.");
		return EnumArmorMaterial.CLOTH;
	}
}
