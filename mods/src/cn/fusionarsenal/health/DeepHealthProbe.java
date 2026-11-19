package cn.fusionarsenal.health;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * Bounded getter data-flow analysis. Writes one reversible health dependency;
 * a composite expression may select its unique subtractive accumulator.
 * Static maps require a getter-proven target key and an existing entry. There
 * are no field-name guesses, trial writes, object scans or death operations.
 */
public final class DeepHealthProbe {
    private DeepHealthProbe() {}
    private static final int MAX_DEPTH = 8, MAX_INSTRUCTIONS = 512, MAX_CLASS_BYTES = 2 * 1024 * 1024;

    public record Snapshot(boolean supported, float health, String reason, String slot) {}
    public enum CommitState { NOT_COMMITTED, CONFIRMED_COMMIT, CONFIRMED_ROLLBACK, UNKNOWN }
    public record Commit(CommitState state,float health,String reason) {}
    public record MapBinding(Map<?,?> map,Object key,Number value,float health,String slot) {}

    private static final class Unsupported extends Exception {
        Unsupported(String message) { super(message); }
    }
    private static Unsupported unsupported(String reason) { return new Unsupported(reason); }

    private interface Value {
        Object get(Eval eval) throws Exception;
        String id();
        default int depth() { return 0; }
        default boolean global(){return false;}
        default boolean targetKey(){return false;}
    }
    private static final class Eval {
        final LivingEntity target;
        final Map<String, Double> overrides = new HashMap<>();
        Eval(LivingEntity target) { this.target = target; }
    }
    private static Object value(Value value,Eval eval)throws Exception {
        if(value instanceof Numeric numeric)return numeric.eval(eval);
        if(value instanceof FieldValue field&&numericType(field.field.getType()))return numeric(value).eval(eval);
        return value.get(eval);
    }
    private static boolean numericType(Class<?> type){return type==byte.class||type==short.class||type==int.class||type==long.class||type==float.class||type==double.class||Number.class.isAssignableFrom(type);}
    private static boolean categoryTwo(Value value){
        if(value instanceof Constant constant)return constant.value instanceof Long||constant.value instanceof Double;
        if(value instanceof FieldValue field)return field.field.getType()==long.class||field.field.getType()==double.class;
        if(value instanceof Cast cast)return switch(cast.opcode){case Opcodes.I2L,Opcodes.F2L,Opcodes.D2L,Opcodes.I2D,Opcodes.L2D,Opcodes.F2D->true;default->false;};
        if(value instanceof Binary binary)return (binary.opcode-Opcodes.IADD)%4==1||(binary.opcode-Opcodes.IADD)%4==3;
        if(value instanceof Limit limit)return !limit.singlePrecision;
        if(value instanceof Storage storage&&storage.slot instanceof FieldSlot field)return field.path.field.getType()==long.class||field.path.field.getType()==double.class;
        return false;
    }
    private static final Value SELF = new Value() {
        public Object get(Eval eval) { return eval.target; }
        public String id() { return "this"; }
        public boolean targetKey(){return true;}
    };
    private record Constant(Object value) implements Value {
        public Object get(Eval eval) { return value; }
        public String id() { return "constant:" + value; }
    }
    private record FieldValue(Value parent, Field field) implements Value {
        public Object get(Eval eval) throws Exception {
            return local(eval, field.get(Modifier.isStatic(field.getModifiers()) ? null : parent.get(eval)));
        }
        public String id() { return parent.id() + "/" + field.getDeclaringClass().getName() + "." + field.getName(); }
        public int depth() { return 1 + parent.depth(); }
        public boolean global(){return Modifier.isStatic(field.getModifiers())||parent.global();}
        public boolean targetKey(){return !Modifier.isStatic(field.getModifiers())&&parent.targetKey();}
    }
    private static Object local(Eval eval, Object object) throws Unsupported {
        if (object instanceof Entity && object != eval.target) throw unsupported("foreign entity reference");
        return object;
    }

    private interface Slot {
        Number read(Eval eval) throws Exception;
        boolean writable(Eval eval) throws Exception;
        boolean set(Eval eval, double value) throws Exception;
        String id();
        default int depth() { return 0; }
    }
    private record FieldSlot(FieldValue path) implements Slot {
        public Number read(Eval eval) throws Exception { return number(path.get(eval)); }
        public boolean writable(Eval eval) throws Exception {
            Object old = path.get(eval);
            return numeric(old) && (!Modifier.isFinal(path.field.getModifiers()) || atomic(old));
        }
        public boolean set(Eval eval, double value) throws Exception {
            Object parent = path.parent.get(eval), old = path.field.get(parent);
            if (atomic(old)) return setAtomic(old, value);
            if (Modifier.isFinal(path.field.getModifiers())) return false;
            path.field.set(parent, convert(number(old), value));
            return true;
        }
        public String id() { return path.id(); }
        public int depth() { return path.depth(); }
    }
    private record AtomicSlot(Value path) implements Slot {
        public Number read(Eval eval) throws Exception { return number(path.get(eval)); }
        public boolean writable(Eval eval) throws Exception { return atomic(path.get(eval)); }
        public boolean set(Eval eval, double value) throws Exception { return setAtomic(path.get(eval), value); }
        public String id() { return path.id() + "/atomic"; }
        public int depth() { return 1 + path.depth(); }
    }
    private record MapValue(Value path, Value key, Value fallback) implements Value, Slot {
        @SuppressWarnings("unchecked") private Map<Object,Object> map(Eval eval) throws Exception {
            Object object = path.get(eval);
            if (!(object instanceof Map<?,?> map)||!safeMap(map)||path.global()&&!key.targetKey())
                throw unsupported("map read or target-key provenance not proven");
            return (Map<Object,Object>) map;
        }
        public Object get(Eval eval) throws Exception {
            Map<Object,Object> map=map(eval);Object selected=key.get(eval);
            if(!map.containsKey(selected)){if(fallback!=null)return fallback.get(eval);throw unsupported("missing map health key");}
            return local(eval,map.get(selected));
        }
        public Number read(Eval eval) throws Exception { return number(get(eval)); }
        public boolean writable(Eval eval) throws Exception { return map(eval).containsKey(key.get(eval))&&numeric(get(eval)); }
        public boolean set(Eval eval, double value) throws Exception {
            Map<Object,Object> map = map(eval);
            synchronized (map) {
                Object selected=key.get(eval);if(!map.containsKey(selected))return false;Object old=map.get(selected);
                if (atomic(old)) return setAtomic(old, value);
                Object replacement=convert(number(old),value);
                return MapWrite.write(map,selected,old,replacement);
            }
        }
        public String id() { return path.id()+"/map["+key.id()+"]"; }
        public int depth() { return 1 + path.depth(); }
        public boolean global(){return path.global();}
    }
    private static boolean safeMap(Map<?,?> map){
        return MapWrite.safeRead(map);
    }
    private record DataValue(Value path, Value key) implements Value, Slot {
        private SynchedEntityData data(Eval eval) throws Exception {
            if (!(path.get(eval) instanceof SynchedEntityData data)||data.getClass()!=SynchedEntityData.class) throw unsupported("not standard entity data");
            return data;
        }
        @SuppressWarnings("rawtypes") private EntityDataAccessor accessor(Eval eval) throws Exception {
            if (!(key.get(eval) instanceof EntityDataAccessor accessor)) throw unsupported("not entity data accessor");
            return accessor;
        }
        @SuppressWarnings("unchecked") public Object get(Eval eval) throws Exception { return local(eval,data(eval).get(accessor(eval))); }
        public Number read(Eval eval) throws Exception { return number(get(eval)); }
        public boolean writable(Eval eval) throws Exception { return numeric(get(eval)); }
        @SuppressWarnings("unchecked") public boolean set(Eval eval, double value) throws Exception {
            Object old = get(eval);
            if (atomic(old)) return setAtomic(old, value);
            data(eval).set(accessor(eval), convert(number(old), value));
            return true;
        }
        public String id() { return path.id() + "/data[" + key.id() + "]"; }
        public int depth() { return 1 + path.depth(); }
    }
    private record TagValue(Value path, String key) implements Value, Slot {
        private CompoundTag tag(Eval eval) throws Exception {
            if (!(path.get(eval) instanceof CompoundTag tag) || tag.getClass()!=CompoundTag.class || !tag.contains(key, 99))
                throw unsupported("missing numeric NBT health key");
            return tag;
        }
        public Object get(Eval eval) throws Exception { return read(eval); }
        public Number read(Eval eval) throws Exception {
            CompoundTag tag = tag(eval);
            return switch (tag.getTagType(key)) {
                case 1 -> tag.getByte(key); case 2 -> tag.getShort(key); case 3 -> tag.getInt(key);
                case 4 -> tag.getLong(key); case 5 -> tag.getFloat(key); case 6 -> tag.getDouble(key);
                default -> throw unsupported("non-numeric NBT health key");
            };
        }
        public boolean writable(Eval eval) throws Exception { return numeric(read(eval)); }
        public boolean set(Eval eval, double value) throws Exception {
            CompoundTag tag = tag(eval); Number converted = convert(read(eval), value);
            switch (tag.getTagType(key)) {
                case 1 -> tag.putByte(key, converted.byteValue()); case 2 -> tag.putShort(key, converted.shortValue());
                case 3 -> tag.putInt(key, converted.intValue()); case 4 -> tag.putLong(key, converted.longValue());
                case 5 -> tag.putFloat(key, converted.floatValue()); case 6 -> tag.putDouble(key, converted.doubleValue());
                default -> { return false; }
            }
            return true;
        }
        public String id() { return path.id() + "/nbt[" + key + "]"; }
        public int depth() { return 1 + path.depth(); }
    }

    private record Affine(double coefficient, double offset) {}
    private interface Numeric extends Value {
        double eval(Eval eval) throws Exception;
        void slots(LinkedHashMap<String, Slot> output);
        Affine affine(Eval eval, String variable) throws Exception;
        default Object get(Eval eval) throws Exception { return eval(eval); }
    }
    private record Literal(double value) implements Numeric {
        public double eval(Eval eval) { return value; }
        public void slots(LinkedHashMap<String, Slot> output) {}
        public Affine affine(Eval eval, String variable) { return new Affine(0, value); }
        public String id() { return "number:" + value; }
    }
    private record Storage(Slot slot) implements Numeric {
        public double eval(Eval eval) throws Exception {
            Double override = eval.overrides.get(slot.id());
            return override == null ? slot.read(eval).doubleValue() : override;
        }
        public void slots(LinkedHashMap<String, Slot> output) { output.putIfAbsent(slot.id(), slot); }
        public Affine affine(Eval eval, String variable) throws Exception {
            return slot.id().equals(variable) ? new Affine(1, 0) : new Affine(0, eval(eval));
        }
        public String id() { return slot.id(); }
        public int depth() { return slot.depth(); }
    }
    private record Frozen(Value value) implements Numeric {
        public double eval(Eval eval) throws Exception { return number(value.get(eval)).doubleValue(); }
        public void slots(LinkedHashMap<String, Slot> output) {}
        public Affine affine(Eval eval, String variable) throws Exception { return new Affine(0, eval(eval)); }
        public String id() { return value.id(); }
        public int depth() { return value.depth(); }
    }
    private record Cast(Numeric value, int opcode) implements Numeric {
        public double eval(Eval eval) throws Exception {
            double result = value.eval(eval);
            return switch (opcode) {
                case Opcodes.I2F, Opcodes.L2F, Opcodes.D2F -> (float) result;
                case Opcodes.F2I, Opcodes.D2I, Opcodes.L2I -> (int) result;
                case Opcodes.F2L, Opcodes.D2L, Opcodes.I2L -> (long) result;
                case Opcodes.I2B -> (byte) result; case Opcodes.I2S -> (short) result; case Opcodes.I2C -> (char) result;
                default -> result;
            };
        }
        public void slots(LinkedHashMap<String, Slot> output) { value.slots(output); }
        public Affine affine(Eval eval, String variable) throws Exception { return value.affine(eval, variable); }
        public String id() { return "cast(" + value.id() + ")"; }
        public int depth() { return 1 + value.depth(); }
    }
    private record Binary(Numeric left, Numeric right, int opcode) implements Numeric {
        public double eval(Eval eval) throws Exception {
            double a=left.eval(eval), b=right.eval(eval), result;
            result = switch(opcode) {
                case Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD -> a+b;
                case Opcodes.ISUB, Opcodes.LSUB, Opcodes.FSUB, Opcodes.DSUB -> a-b;
                case Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL -> a*b;
                case Opcodes.IDIV, Opcodes.LDIV, Opcodes.FDIV, Opcodes.DDIV -> a/b;
                default -> throw unsupported("numeric operation");
            };
            if (opcode==Opcodes.FADD || opcode==Opcodes.FSUB || opcode==Opcodes.FMUL || opcode==Opcodes.FDIV) return (float)result;
            if (opcode==Opcodes.IADD || opcode==Opcodes.ISUB || opcode==Opcodes.IMUL || opcode==Opcodes.IDIV) return (int)result;
            if (opcode==Opcodes.LADD || opcode==Opcodes.LSUB || opcode==Opcodes.LMUL || opcode==Opcodes.LDIV) return (long)result;
            return result;
        }
        public void slots(LinkedHashMap<String, Slot> output) { left.slots(output); right.slots(output); }
        public Affine affine(Eval eval, String variable) throws Exception {
            Affine a=left.affine(eval,variable), b=right.affine(eval,variable);
            if (opcode>=Opcodes.IADD && opcode<=Opcodes.DADD) return new Affine(a.coefficient+b.coefficient,a.offset+b.offset);
            if (opcode>=Opcodes.ISUB && opcode<=Opcodes.DSUB) return new Affine(a.coefficient-b.coefficient,a.offset-b.offset);
            if (opcode>=Opcodes.IMUL && opcode<=Opcodes.DMUL) {
                if (a.coefficient!=0 && b.coefficient!=0) throw unsupported("non-linear health expression");
                return new Affine(a.coefficient*b.offset+b.coefficient*a.offset,a.offset*b.offset);
            }
            if (b.coefficient!=0 || b.offset==0) throw unsupported("non-invertible health division");
            return new Affine(a.coefficient/b.offset,a.offset/b.offset);
        }
        public String id() { return "expression"; }
        public int depth() { return 1 + Math.max(left.depth(),right.depth()); }
    }
    private record Compare(Value left,Value right,int opcode) implements Value {
        public Object get(Eval eval)throws Exception{
            Object a=value(left,eval),b=value(right,eval);
            double x=((Number)a).doubleValue(),y=((Number)b).doubleValue();
            if(Double.isNaN(x)||Double.isNaN(y))return opcode==Opcodes.FCMPL||opcode==Opcodes.DCMPL?-1:1;
            return x<y?-1:x>y?1:0; // JVM floating comparison treats both signed zeros as equal.
        }
        public String id(){return "comparison";}
    }
    private record Limit(Numeric input,Numeric bound,boolean minimum,boolean singlePrecision) implements Numeric {
        public double eval(Eval eval)throws Exception{
            double a=input.eval(eval),b=bound.eval(eval),result=minimum?Math.min(a,b):Math.max(a,b);
            return singlePrecision?(float)result:result;
        }
        public void slots(LinkedHashMap<String,Slot> output){input.slots(output);bound.slots(output);}
        public Affine affine(Eval eval,String variable)throws Exception{
            Affine a=input.affine(eval,variable),b=bound.affine(eval,variable);
            if(a.coefficient!=0&&b.coefficient!=0)throw unsupported("multiple variable clamp inputs");
            return a.coefficient!=0?a:b.coefficient!=0?b:new Affine(0,eval(eval));
        }
        public String id(){return "limit";}
        public int depth(){return 1+Math.max(input.depth(),bound.depth());}
    }
    private record Guard(Value receiver, Class<?> declaring, String name, String descriptor, boolean virtual) {
        void check(Eval eval) throws Exception {
            Object object=receiver.get(eval);
            if (object==null) throw unsupported("null getter receiver");
            if (virtual && find(object.getClass(),name,descriptor).owner!=declaring)
                throw unsupported("nested getter dispatch changed");
        }
    }
    private record Method(Class<?> owner, MethodNode node) {}
    private static final class CachedClass {long revision=Long.MIN_VALUE;ClassNode node;}
    private static final ClassValue<CachedClass> CLASSES=new ClassValue<>(){
        protected CachedClass computeValue(Class<?> type){return new CachedClass();}
    };
    private static long revision(Class<?> type){
        return ClassSnapshotAccess.revision(type);
    }
    private static ClassNode clazz(Class<?> type)throws Exception{
        CachedClass cached=CLASSES.get(type);long revision=revision(type);
        synchronized(cached){
            if(cached.node==null||cached.revision!=revision){
                ClassNode node=new ClassNode();new ClassReader(classBytes(type)).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                cached.node=node;cached.revision=revision;
            }
            return cached.node;
        }
    }
    static ClassNode classNode(Class<?> type)throws Exception{return clazz(type);}
    static Object pureCall(LivingEntity target,Class<?> owner,String name,String descriptor,Object receiver,List<Object> arguments,int opcode)throws Exception{
        Eval eval=new Eval(target);Parser parser=new Parser(eval);List<Value> args=new ArrayList<>();
        for(Object argument:arguments)args.add(argument==target?SELF:new Constant(argument));
        Value object=receiver==target?SELF:new Constant(receiver),result;
        if(opcode==Opcodes.INVOKESTATIC)result=parser.parse(find(owner,name,descriptor),object,args,0);
        else result=parser.call(owner,new MethodInsnNode(opcode,Type.getInternalName(owner),name,descriptor,false),object,args,0);
        for(Guard guard:parser.guards)guard.check(eval);return value(result,eval);
    }
    static Number represented(Number old,double value)throws Exception{return convert(old,value);}
    private static MapBinding mapBinding(Inspected found)throws Exception{
        if(!(found.slot instanceof MapValue map)||!map.key.targetKey())return null;
        Object key=map.key.get(found.eval);if(key!=found.eval.target)return null;
        return new MapBinding(map.map(found.eval),key,map.read(found.eval),found.health,map.id());
    }
    public static MapBinding primaryMap(LivingEntity target){
        try{return mapBinding(inspectInternal(target));}catch(Exception|LinkageError ignored){return null;}
    }
    private static Method root(Class<?> type)throws Exception{
        java.util.Set<String> names=new java.util.HashSet<>();
        for(String name:new String[]{"getHealth","m_21223_"})try{
            if(LivingEntity.class.getDeclaredMethod(name).getReturnType()==float.class)names.add(name);
        }catch(NoSuchMethodException ignored){}
        for(Class<?> owner=type;owner!=null&&owner!=LivingEntity.class;owner=owner.getSuperclass())
            for(MethodNode method:clazz(owner).methods)
                if(names.contains(method.name)&&method.desc.equals("()F"))return new Method(owner,method);
        throw unsupported("no foreign health getter");
    }
    private static Numeric expression(Eval eval)throws Exception{
        Parser parser=new Parser(eval);Numeric expression=numeric(parser.parse(root(eval.target.getClass()),SELF,List.of(),0));
        for(Guard guard:parser.guards)guard.check(eval);return expression;
    }

    /** Prefer launch-stage observations; resources are only an observation fallback. */
    private static byte[] classBytes(Class<?> type) throws Exception {
        return ClassSnapshotAccess.read(type);
    }
    private static String reason(Throwable failure) {
        return failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();
    }
    private record Inspected(Eval eval, Numeric expression, Slot slot, float health) {}
    private static Inspected inspectInternal(LivingEntity target) throws Exception {
        if(target==null)throw unsupported("null target");
        Eval eval=new Eval(target);
        Numeric expression=expression(eval);
        LinkedHashMap<String,Slot> slots=new LinkedHashMap<>();expression.slots(slots);
        Slot selected=null,negative=null;int writable=0,negativeCount=0;
        for(Slot slot:slots.values()) {
            number(slot.read(eval));
            if(!slot.writable(eval))continue;
            writable++;selected=slot;
            try{Affine candidate=expression.affine(eval,slot.id());
                if(candidate.coefficient<0){negative=slot;negativeCount++;}
            }catch(Unsupported ignored){}
        }
        if(selected==null)throw unsupported("no writable health dependency");
        if(writable>1){if(negativeCount!=1)throw unsupported("ambiguous composite health dependencies");selected=negative;}
        Affine affine=expression.affine(eval,selected.id());
        if(!Double.isFinite(affine.coefficient)||affine.coefficient==0||!Double.isFinite(affine.offset))
            throw unsupported("non-invertible health expression");
        float health=(float)expression.eval(eval);
        if(!Float.isFinite(health)||health<0)throw unsupported("non-finite or negative health");
        return new Inspected(eval,expression,selected,health);
    }
    public static Snapshot inspect(LivingEntity target) {
        try { Inspected found=inspectInternal(target); return new Snapshot(true,found.health,"unique reversible slot",found.slot.id()); }
        catch(Exception|LinkageError failure){return new Snapshot(false,Float.NaN,reason(failure),null);}
    }
    public static float read(LivingEntity target,float fallback) {
        Snapshot found=inspect(target);return found.supported?found.health:fallback;
    }
    public static boolean write(LivingEntity target,float expectedHealth) {
        return commit(target,expectedHealth).state==CommitState.CONFIRMED_COMMIT;
    }
    public static Commit commit(LivingEntity target,float expectedHealth) {
        if(target==null||!Float.isFinite(expectedHealth)||expectedHealth<0)return new Commit(CommitState.NOT_COMMITTED,Float.NaN,"invalid target or health");
        synchronized(target) {
            Inspected found=null;double old=Double.NaN, candidate=Double.NaN;
            MirrorHealthProbe.Plan mirrors=null;boolean attempted=false;String failure="verification rejected";
            try {
                found=inspectInternal(target);old=found.slot.read(found.eval).doubleValue();
                Affine affine=found.expression.affine(found.eval,found.slot.id());
                candidate=(expectedHealth-affine.offset)/affine.coefficient;
                Number represented=convert(found.slot.read(found.eval),candidate);candidate=represented.doubleValue();
                found.eval.overrides.put(found.slot.id(),candidate);
                if(Float.compare((float)expression(found.eval).eval(found.eval),expectedHealth)!=0)return new Commit(CommitState.NOT_COMMITTED,found.health,"inverse cannot represent requested health");
                found.eval.overrides.clear();
                MapBinding primary=mapBinding(found);
                if(primary!=null)mirrors=MirrorHealthProbe.prepare(target,primary);
                attempted=true;
                boolean accepted=found.slot.set(found.eval,candidate);
                if(accepted&&mirrors!=null)mirrors.write(candidate);
                if(accepted&&Float.compare((float)expression(found.eval).eval(found.eval),expectedHealth)==0){
                    if(primary!=null){MapBinding after=mapBinding(inspectInternal(target));if(after==null||after.map!=primary.map||after.key!=primary.key)throw unsupported("primary map identity changed");}
                    if(mirrors==null||mirrors.verify(candidate))return new Commit(CommitState.CONFIRMED_COMMIT,expectedHealth,"getter storage and proven mirrors verified");
                }
            } catch(Exception|LinkageError error) {
                failure=reason(error);
                // Only a proven slot may have been touched; no alternative fields are tried.
            }
            if(!attempted)return new Commit(CommitState.NOT_COMMITTED,found==null?Float.NaN:found.health,failure);
            boolean restored=true;
            if(mirrors!=null)restored=mirrors.restore();
            if(found!=null&&Double.isFinite(old))try {
                found.eval.overrides.clear();
                if(Double.compare(found.slot.read(found.eval).doubleValue(),old)!=0)found.slot.set(found.eval,old);
                restored&=Double.compare(found.slot.read(found.eval).doubleValue(),old)==0
                    &&Float.compare((float)expression(found.eval).eval(found.eval),found.health)==0;
            } catch(Exception|LinkageError ignored) {restored=false;}
            return new Commit(restored?CommitState.CONFIRMED_ROLLBACK:CommitState.UNKNOWN,restored?found.health:Float.NaN,failure);
        }
    }

    private static boolean atomic(Object object) {return object!=null&&(object.getClass()==AtomicInteger.class||object.getClass()==AtomicLong.class);}
    private static boolean numeric(Object object) {
        return object instanceof Byte||object instanceof Short||object instanceof Integer||object instanceof Long
            ||object instanceof Float||object instanceof Double||(object!=null&&(object.getClass()==BigInteger.class||object.getClass()==BigDecimal.class))||atomic(object);
    }
    private static Number number(Object object) throws Unsupported {
        if(!numeric(object))throw unsupported("unsupported numeric storage");
        Number result=(Number)object;
        if(!Double.isFinite(result.doubleValue()))throw unsupported("non-finite numeric storage");
        return result;
    }
    private static Number convert(Number original,double value) throws Unsupported {
        if(!Double.isFinite(value))throw unsupported("non-finite inverse");
        if(original instanceof Float) {float result=(float)value;if(!Float.isFinite(result))throw unsupported("float range");return result;}
        if(original instanceof Double)return value;
        if(original instanceof BigDecimal)return BigDecimal.valueOf(value);
        if(value!=Math.rint(value))throw unsupported("fractional integral health storage");
        if(original instanceof Byte&&value>=Byte.MIN_VALUE&&value<=Byte.MAX_VALUE)return (byte)value;
        if(original instanceof Short&&value>=Short.MIN_VALUE&&value<=Short.MAX_VALUE)return (short)value;
        if((original instanceof Integer||original instanceof AtomicInteger)&&value>=Integer.MIN_VALUE&&value<=Integer.MAX_VALUE)return (int)value;
        if((original instanceof Long||original instanceof AtomicLong||original instanceof BigInteger)&&Math.abs(value)<=9007199254740991D)
            return original instanceof BigInteger?BigDecimal.valueOf(value).toBigIntegerExact():(long)value;
        throw unsupported("integral health range");
    }
    private static boolean setAtomic(Object original,double value) throws Unsupported {
        if(original instanceof AtomicInteger atomic) {atomic.set(convert(atomic,value).intValue());return true;}
        if(original instanceof AtomicLong atomic) {atomic.set(convert(atomic,value).longValue());return true;}
        return false;
    }
    private static Numeric numeric(Value value) throws Exception {
        if(value instanceof Numeric numeric)return numeric;
        if(value instanceof Constant constant)return new Literal(number(constant.value).doubleValue());
        if(value instanceof FieldValue field) {
            if(Modifier.isStatic(field.field.getModifiers()))return new Frozen(value);
            return new Storage(new FieldSlot(field));
        }
        if(value instanceof Slot slot)return new Storage(slot);
        throw unsupported("numeric value has no proven storage");
    }
    private static Field field(Class<?> context,FieldInsnNode instruction) throws Exception {
        Class<?> owner=Class.forName(instruction.owner.replace('/','.'),false,context.getClassLoader());
        for(Class<?> type=owner;type!=null;type=type.getSuperclass())try {
            Field field=type.getDeclaredField(instruction.name);field.setAccessible(true);return field;
        } catch(NoSuchFieldException ignored) {}
        throw unsupported("field unavailable");
    }
    private static Method find(Class<?> owner,String name,String descriptor) throws Exception {
        for(Class<?> type=owner;type!=null;type=type.getSuperclass())
            for(MethodNode method:clazz(type).methods)
                if(method.name.equals(name)&&method.desc.equals(descriptor))return new Method(type,method);
        throw unsupported("getter method unavailable");
    }
    private static final ClassValue<Map<String,MethodHandle>> NATIVE_READS=new ClassValue<>(){
        protected Map<String,MethodHandle> computeValue(Class<?> type){return new HashMap<>();}
    };
    private static Object nativeRead(Object receiver,Method method)throws Exception{
        Map<String,MethodHandle> handles=NATIVE_READS.get(method.owner);MethodHandle handle;
        synchronized(handles){
            handle=handles.get(method.node.name);
            if(handle==null){
                java.lang.reflect.Method reflected=method.owner.getDeclaredMethod(method.node.name);
                MethodHandles.Lookup lookup=MethodHandles.privateLookupIn(method.owner,MethodHandles.lookup());
                handle=lookup.findSpecial(method.owner,method.node.name,MethodType.methodType(reflected.getReturnType()),method.owner);
                handles.put(method.node.name,handle);
            }
        }
        try{return handle.invoke(receiver);}catch(Throwable failure){throw unsupported("native read failed: "+reason(failure));}
    }

    private static final class Parser {
        final Eval eval;
        Parser(Eval eval){this.eval=eval;}
        final List<Guard> guards=new ArrayList<>();
        final List<String> calls=new ArrayList<>();int instructions;
        Value parse(Method method,Value receiver,List<Value> arguments,int depth) throws Exception {
            if(depth>MAX_DEPTH)throw unsupported("getter recursion budget");
            String signature=method.owner.getName()+"."+method.node.name+method.node.desc;
            if(calls.contains(signature))throw unsupported("recursive getter cycle");
            if((method.node.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))!=0)
                throw unsupported("non-instance or exceptional getter");
            calls.add(signature);
            try {
                boolean isStatic=(method.node.access&Opcodes.ACC_STATIC)!=0;
                Map<Integer,Value> locals=new HashMap<>();if(!isStatic)locals.put(0,receiver);int index=isStatic?0:1;
                Type[] types=Type.getArgumentTypes(method.node.desc);
                for(int i=0;i<arguments.size();i++){locals.put(index,arguments.get(i));index+=types[i].getSize();}
                List<Value> stack=new ArrayList<>();
                AbstractInsnNode[] code=method.node.instructions.toArray();
                Map<LabelNode,Integer> labels=new HashMap<>();for(int n=0;n<code.length;n++)if(code[n] instanceof LabelNode label)labels.put(label,n);
                for(int pc=0;pc<code.length;pc++) {
                    AbstractInsnNode instruction=code[pc];
                    int opcode=instruction.getOpcode();if(opcode<0)continue;
                    if(++instructions>MAX_INSTRUCTIONS)throw unsupported("getter instruction budget");
                    if(instruction instanceof VarInsnNode variable) {
                        if(opcode>=Opcodes.ILOAD&&opcode<=Opcodes.ALOAD) {
                            Value value=locals.get(variable.var);if(value==null)throw unsupported("unbound getter local");stack.add(value);continue;
                        }
                        if(opcode>=Opcodes.ISTORE&&opcode<=Opcodes.ASTORE){locals.put(variable.var,pop(stack));continue;}
                    }
                    if(instruction instanceof LdcInsnNode constant) {
                        if(!(constant.cst instanceof Number||constant.cst instanceof String))throw unsupported("non-literal constant");
                        stack.add(new Constant(constant.cst));continue;
                    }
                    if(opcode>=Opcodes.ICONST_M1&&opcode<=Opcodes.ICONST_5){stack.add(new Constant(opcode-Opcodes.ICONST_0));continue;}
                    if(opcode==Opcodes.LCONST_0||opcode==Opcodes.LCONST_1){stack.add(new Constant((long)(opcode-Opcodes.LCONST_0)));continue;}
                    if(opcode>=Opcodes.FCONST_0&&opcode<=Opcodes.FCONST_2){stack.add(new Constant((float)(opcode-Opcodes.FCONST_0)));continue;}
                    if(opcode==Opcodes.DCONST_0||opcode==Opcodes.DCONST_1){stack.add(new Constant((double)(opcode-Opcodes.DCONST_0)));continue;}
                    if(instruction instanceof IntInsnNode integer&&(opcode==Opcodes.BIPUSH||opcode==Opcodes.SIPUSH)){
                        stack.add(new Constant(integer.operand));continue;
                    }
                    if(opcode==Opcodes.NOP||opcode==Opcodes.CHECKCAST)continue;
                    if(opcode==Opcodes.ACONST_NULL){stack.add(new Constant(null));continue;}
                    if(instruction instanceof JumpInsnNode jump){
                        int destination=labels.get(jump.label);if(destination<=pc)throw unsupported("getter control-flow cycle");
                        boolean take;
                        if(opcode==Opcodes.GOTO)take=true;
                        else if(opcode==Opcodes.IFNULL||opcode==Opcodes.IFNONNULL){Object object=pop(stack).get(eval);take=(object==null)==(opcode==Opcodes.IFNULL);}
                        else if(opcode==Opcodes.IF_ACMPEQ||opcode==Opcodes.IF_ACMPNE){Object b=pop(stack).get(eval),a=pop(stack).get(eval);take=(a==b)==(opcode==Opcodes.IF_ACMPEQ);}
                        else if(opcode>=Opcodes.IF_ICMPEQ&&opcode<=Opcodes.IF_ICMPLE){double b=scalar(pop(stack)),a=scalar(pop(stack));take=condition(opcode-Opcodes.IF_ICMPEQ,a,b);}
                        else if(opcode>=Opcodes.IFEQ&&opcode<=Opcodes.IFLE)take=condition(opcode-Opcodes.IFEQ,scalar(pop(stack)),0);
                        else throw unsupported("getter branch opcode");
                        if(take)pc=destination-1;continue;
                    }
                    if(opcode>=Opcodes.LCMP&&opcode<=Opcodes.DCMPG){Value b=pop(stack),a=pop(stack);stack.add(new Compare(a,b,opcode));continue;}
                    if(opcode==Opcodes.DUP){Value value=pop(stack);stack.add(value);stack.add(value);continue;}
                    if(opcode==Opcodes.SWAP){Value a=pop(stack),b=pop(stack);if(categoryTwo(a)||categoryTwo(b))throw unsupported("SWAP requires category-one values");stack.add(a);stack.add(b);continue;}
                    if(opcode==Opcodes.POP){pop(stack);continue;}
                    if(instruction instanceof FieldInsnNode access&&(opcode==Opcodes.GETFIELD||opcode==Opcodes.GETSTATIC)) {
                        Field resolved=field(method.owner,access);Value parent=opcode==Opcodes.GETSTATIC?new Constant(null):pop(stack);
                        if(opcode==Opcodes.GETSTATIC && !Map.class.isAssignableFrom(resolved.getType())&&(!Modifier.isFinal(resolved.getModifiers())
                            ||!(resolved.getType().isPrimitive()||resolved.getType()==String.class||resolved.getType()==EntityDataAccessor.class)))
                            throw unsupported("global mutable/static object dependency");
                        if(parent.depth()>=32)throw unsupported("health path budget");
                        stack.add(new FieldValue(parent,resolved));continue;
                    }
                    if(opcode>=Opcodes.IADD&&opcode<=Opcodes.DDIV){Numeric right=numeric(pop(stack)),left=numeric(pop(stack));stack.add(new Binary(left,right,opcode));continue;}
                    if(opcode>=Opcodes.INEG&&opcode<=Opcodes.DNEG){stack.add(new Binary(new Literal(0),numeric(pop(stack)),Opcodes.DSUB));continue;}
                    if(opcode>=Opcodes.I2L&&opcode<=Opcodes.I2S){stack.add(new Cast(numeric(pop(stack)),opcode));continue;}
                    if(instruction instanceof MethodInsnNode call) {
                        List<Value> args=new ArrayList<>();for(int i=0;i<Type.getArgumentTypes(call.desc).length;i++)args.add(0,pop(stack));
                        if(opcode==Opcodes.INVOKESTATIC){
                            Value result=intrinsic(call,args);
                            if(result==null){
                                Class<?> owner=Class.forName(call.owner.replace('/','.'),false,method.owner.getClassLoader());
                                if(owner.getName().startsWith("java.")||owner.getName().startsWith("javax."))throw unsupported("unsupported static library call");
                                Method delegated=find(owner,call.name,call.desc);
                                if((delegated.node.access&Opcodes.ACC_STATIC)==0)throw unsupported("static method mismatch");
                                result=parse(delegated,new Constant(null),args,depth+1);
                            }
                            stack.add(result);continue;
                        }
                        Value object=pop(stack);stack.add(call(method.owner,call,object,args,depth));continue;
                    }
                    if(opcode>=Opcodes.IRETURN&&opcode<=Opcodes.ARETURN) {
                        Value result=pop(stack); // A JVM return discards the complete frame, including older operands.
                        if(result.depth()>64)throw unsupported("health expression depth budget");return result;
                    }
                    throw unsupported("branch, side effect or unsupported getter opcode "+opcode);
                }
                throw unsupported("getter has no value return");
            } finally {calls.remove(calls.size()-1);}
        }
        Value call(Class<?> context,MethodInsnNode call,Value object,List<Value> arguments,int depth) throws Exception {
            if(arguments.isEmpty()&&(call.name.equals("level")||call.name.equals("m_9236_")))return new Value(){
                public Object get(Eval eval)throws Exception{if(object.get(eval)!=eval.target)throw unsupported("foreign level receiver");return eval.target.level();}
                public String id(){return object.id()+"/level";}
            };
            if(arguments.isEmpty()&&(call.name.equals("getMaxHealth")||call.name.equals("m_21233_")||call.name.equals("getAttributes")||call.name.equals("m_21204_"))){
                if(object.get(eval)!=eval.target)throw unsupported("foreign standard read receiver");
                Class<?> declared=Class.forName(call.owner.replace('/','.'),false,context.getClassLoader());
                Method selected=find(call.getOpcode()==Opcodes.INVOKESPECIAL?declared:eval.target.getClass(),call.name,call.desc);
                boolean maximum=call.name.equals("getMaxHealth")||call.name.equals("m_21233_");
                Value read;
                if(selected.owner.getName().startsWith("net.minecraft."))read=new Value(){
                    public Object get(Eval eval)throws Exception{return nativeRead(eval.target,selected);}
                    public String id(){return object.id()+"/native:"+call.name;}
                };
                else read=parse(selected,object,arguments,depth+1);
                return maximum?new Frozen(read):read;
            }
            if(call.owner.equals("net/minecraft/world/level/Level")&&arguments.isEmpty()&&(call.name.equals("isClientSide")||call.name.equals("m_5776_")))return new Value(){
                public Object get(Eval eval)throws Exception{Object level=object.get(eval);if(level!=eval.target.level())throw unsupported("foreign level");return eval.target.level().isClientSide;}
                public String id(){return object.id()+"/client";}
            };
            if(call.name.equals("getEntityData")||call.name.equals("m_20088_")) {
                if(!call.desc.equals("()Lnet/minecraft/network/syncher/SynchedEntityData;"))throw unsupported("entity data descriptor");
                return new Value() {
                    public Object get(Eval eval)throws Exception {
                        Object receiver=object.get(eval);if(receiver!=eval.target)throw unsupported("foreign entity data");
                        return eval.target.getEntityData();
                    }
                    public String id(){return object.id()+"/entityData";}
                    public int depth(){return 1+object.depth();}
                };
            }
            if(call.owner.equals("net/minecraft/network/syncher/SynchedEntityData")
                &&(call.name.equals("get")||call.name.equals("m_135370_"))&&arguments.size()==1)
                return new DataValue(object,arguments.get(0));
            if((call.owner.equals("java/util/concurrent/atomic/AtomicInteger")||call.owner.equals("java/util/concurrent/atomic/AtomicLong"))
                &&call.name.equals("get")&&arguments.isEmpty())return new Storage(new AtomicSlot(object));
            if((call.owner.equals("java/lang/Number")||call.owner.equals("java/lang/Float")||call.owner.equals("java/lang/Double")
                ||call.owner.equals("java/lang/Integer")||call.owner.equals("java/lang/Long")||call.owner.equals("java/lang/Short")
                ||call.owner.equals("java/lang/Byte")||call.owner.equals("java/math/BigDecimal")||call.owner.equals("java/math/BigInteger")
                ||call.owner.equals("java/util/concurrent/atomic/AtomicInteger")||call.owner.equals("java/util/concurrent/atomic/AtomicLong"))
                &&arguments.isEmpty()&&(call.name.equals("floatValue")||call.name.equals("doubleValue")
                ||call.name.equals("intValue")||call.name.equals("longValue"))) {
                Numeric value=numeric(object);
                return call.name.equals("floatValue")?new Cast(value,Opcodes.D2F):call.name.equals("intValue")?new Cast(value,Opcodes.D2I)
                    :call.name.equals("longValue")?new Cast(value,Opcodes.D2L):value;
            }
            Class<?> owner=Class.forName(call.owner.replace('/','.'),false,context.getClassLoader());
            if(Map.class.isAssignableFrom(owner)&&(call.name.equals("get")&&arguments.size()==1||call.name.equals("getOrDefault")&&arguments.size()==2)){
                Value key=arguments.get(0);
                if(!key.targetKey())key=new Constant(key(key));
                return new MapValue(object,key,arguments.size()==2?arguments.get(1):null);
            }
            if(call.owner.equals("net/minecraft/nbt/CompoundTag")&&arguments.size()==1) {
                String key=String.valueOf(key(arguments.get(0)));
                if(call.name.equals("getFloat")||call.name.equals("m_128457_")||call.name.equals("getDouble")||call.name.equals("m_128459_"))
                    return call.name.equals("getFloat")||call.name.equals("m_128457_")
                        ?new Cast(new Storage(new TagValue(object,key)),Opcodes.D2F):new Storage(new TagValue(object,key));
                if(call.name.equals("getCompound")||call.name.equals("m_128469_"))return new Value(){
                    public Object get(Eval eval)throws Exception{
                        Object value=object.get(eval);if(!(value instanceof CompoundTag tag)||tag.getClass()!=CompoundTag.class||!tag.contains(key,10))throw unsupported("missing compound health path");
                        return tag.getCompound(key);
                    }
                    public String id(){return object.id()+"/compound["+key+"]";}
                    public int depth(){return 1+object.depth();}
                };
            }
            if(owner.getName().startsWith("java.")||owner.getName().startsWith("javax."))throw unsupported("unsupported library getter");
            Class<?> dispatch=call.getOpcode()==Opcodes.INVOKESPECIAL?owner:object.get(eval).getClass();
            Method method=find(dispatch,call.name,call.desc);
            guards.add(new Guard(object,method.owner,call.name,call.desc,
                call.getOpcode()!=Opcodes.INVOKESPECIAL&&(method.node.access&Opcodes.ACC_PRIVATE)==0));
            return parse(method,object,arguments,depth+1);
        }
        Object key(Value value)throws Exception {
            if(value instanceof Constant constant&&(constant.value instanceof String||constant.value instanceof Number))return constant.value;
            if(value instanceof FieldValue field&&Modifier.isStatic(field.field.getModifiers())&&field.field.getType()==String.class)
                return field.field.get(null);
            throw unsupported("non-constant health container key");
        }
        Value intrinsic(MethodInsnNode call,List<Value> arguments)throws Exception{
            if(call.owner.equals("cn/fusionarsenal/CreativeRuntimeHooks")&&call.name.equals("health")
                &&call.desc.equals("(Ljava/lang/Object;F)F")&&arguments.size()==2)return arguments.get(1);
            if((call.owner.equals("java/lang/Float")||call.owner.equals("java/lang/Double")||call.owner.equals("java/lang/Integer")||call.owner.equals("java/lang/Long")
                ||call.owner.equals("java/lang/Short")||call.owner.equals("java/lang/Byte"))&&call.name.equals("valueOf")&&arguments.size()==1)return arguments.get(0);
            if(call.owner.equals("java/lang/Math")&&(call.name.equals("min")||call.name.equals("max"))&&arguments.size()==2)
                return new Limit(numeric(arguments.get(0)),numeric(arguments.get(1)),call.name.equals("min"),call.desc.endsWith("F"));
            if(call.owner.equals("net/minecraft/util/Mth")&&(call.name.equals("clamp")||call.name.equals("m_14036_")||call.name.equals("m_14008_"))&&arguments.size()==3){
                Numeric low=numeric(arguments.get(1)),high=numeric(arguments.get(2));boolean single=call.desc.endsWith("F");
                return new Limit(new Limit(numeric(arguments.get(0)),low,false,single),high,true,single);
            }
            if(call.owner.equals("java/lang/Float")&&call.name.equals("isFinite")&&arguments.size()==1)return new Value(){
                public Object get(Eval eval)throws Exception{return Float.isFinite(((Number)value(arguments.get(0),eval)).floatValue());}
                public String id(){return "finite";}
            };
            return null;
        }
        double scalar(Value value)throws Exception{Object object=value(value,eval);return object instanceof Boolean bool?bool?1:0:((Number)object).doubleValue();}
        boolean condition(int kind,double a,double b)throws Unsupported{return switch(kind){
            case 0->a==b;case 1->a!=b;case 2->a<b;case 3->a>=b;case 4->a>b;case 5->a<=b;default->throw unsupported("branch condition");};}
        Value pop(List<Value> stack)throws Unsupported{
            if(stack.isEmpty())throw unsupported("invalid getter stack");return stack.remove(stack.size()-1);
        }
    }
}
