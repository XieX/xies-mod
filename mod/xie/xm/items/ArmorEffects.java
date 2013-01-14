package mod.xie.xm.items;

import java.util.HashMap;

import net.minecraft.util.DamageSource;

import argo.jdom.JsonNode;

public class ArmorEffects {
	
	private HashMap<String,Integer> resistances;
	
	public ArmorEffects() {
		resistances = new HashMap<String,Integer>();
	}
	
	public void addResistance(String s, int n) {
		if (n==0) return;
		resistances.put(s, n);
	}
	
	public int getResistanceTo(DamageSource source) {
		if (resistances.isEmpty()) return 0;
		
		if (resistances.containsKey(source.getDamageType())) {
			return resistances.get(source.getDamageType());
		}
		
		return 0;
	}
	
}
