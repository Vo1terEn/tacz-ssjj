package cn.ssjjhyper.client;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.IKeyConflictContext;

/** One shared native key gate for overlapping V and G animation locks. */
final class HyperActionKeys {
    static final int CHARGE=1,CORE=2;
    private HyperActionKeys(){}
    static void update(int owner,boolean locked,KeyMapping... keys){
        for(var key:keys){
            var current=key.getKeyConflictContext();
            var gate=current instanceof Context existing?existing:new Context(current);
            if(gate!=current)key.setKeyConflictContext(gate);
            gate.locks=locked?gate.locks|owner:gate.locks&~owner;
        }
    }
    private static final class Context implements IKeyConflictContext {
        private final IKeyConflictContext original;
        private int locks;
        Context(IKeyConflictContext original){this.original=original;}
        public boolean isActive(){return locks==0&&original.isActive();}
        public boolean conflicts(IKeyConflictContext other){return original.conflicts(other instanceof Context gate?gate.original:other);}
    }
}
