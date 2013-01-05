package xie.mods.xm.util;

import java.util.Random;

import net.minecraft.potion.PotionEffect;
 
public class PotionEffectWrapper extends PotionEffect {

	protected float chance;
	
	public PotionEffectWrapper(int i, int j, int k, float f) {
		super(i, j, k);
		chance = f;
	}
	
	public PotionEffectWrapper(int i, int j, int k) {
		this(i, j, k, 1);
	}
	
	public float getChance() { return chance; }

	public boolean testChance(Random rand) {
		if (chance>=1) return true;
		else return rand.nextFloat() < chance;
	}
}

