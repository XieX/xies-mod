package mod.xie.xm.blocks;

import net.minecraft.block.material.Material;

public class XieBlockGlass extends XieBlock {

	public XieBlockGlass(String n, int id, Material mat) {
		super(id, mat, n);
	}
	
    public XieBlockGlass(String name, int id) {
		this(name,id,Material.glass);
	}

	@Override
	public boolean isOpaqueCube() {
    	return false;
    }
	
	public int getRenderBlockPass() {
        return 0;
    }
	
}
