package cn.fusionarsenal.health;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Finds same-target mirror-to-primary copies in reachable lifecycle bytecode, without executing it. */
public final class MirrorHealthProbe {
    private MirrorHealthProbe() {}
    private interface Locator { Object get() throws Exception; }
    private record MapRef(Map<?,?> map,Locator locator) {}
    private record Read(MapRef map,Object key) {}
    private record Callback(Handle handle,List<V> captured,Class<?> context) {}
    private record Comparison(V left,V right,boolean booleanEquality) {}
    private record V(Object object,Read source,Callback callback) {
        static V object(Object object){return new V(object,null,null);}
        static V read(Object value,Read source){return new V(value,source,null);}
    }
    private static final V UNKNOWN=V.object(null);
    private record Method(Class<?> owner,MethodNode code) {}
    private record State(int pc,Map<Integer,V> locals,List<V> stack,List<Read> guards) {}
    private record Mirror(MapRef ref,Object key,Number before) {
        Number current()throws Exception{
            if(ref.locator.get()!=ref.map||!ref.map.containsKey(key)||!MapWrite.safeRead(ref.map))throw new IllegalStateException("mirror map identity changed");
            Object result=ref.map.get(key);
            if(!(result instanceof Number number)||!Double.isFinite(number.doubleValue()))throw new IllegalStateException("invalid mirror value");
            return number;
        }
    }
    public static final class Plan {
        private final List<Mirror> mirrors;
        private Plan(List<Mirror> mirrors){this.mirrors=List.copyOf(mirrors);}
        public int size(){return mirrors.size();}
        void write(double value)throws Exception{
            for(Mirror mirror:mirrors){Number replacement=DeepHealthProbe.represented(mirror.before,value);
                if(!Objects.equals(mirror.current(),mirror.before)||!MapWrite.write(mirror.ref.map,mirror.key,mirror.before,replacement))throw new IllegalStateException("mirror write refused");}
        }
        boolean verify(double value)throws Exception{
            for(Mirror mirror:mirrors)if(!Objects.equals(mirror.current(),DeepHealthProbe.represented(mirror.before,value)))return false;
            return true;
        }
        boolean restore(){
            boolean restored=true;
            for(int i=mirrors.size()-1;i>=0;i--){Mirror mirror=mirrors.get(i);try{
                Number current=mirror.current();
                if(!Objects.equals(current,mirror.before))MapWrite.write(mirror.ref.map,mirror.key,current,mirror.before);
                restored&=Objects.equals(mirror.current(),mirror.before);
            }catch(Exception|LinkageError ignored){restored=false;}}
            return restored;
        }
    }
    public static Plan prepare(LivingEntity target,DeepHealthProbe.MapBinding primary)throws Exception{
        if(primary.key()!=target||!MapWrite.safeRead(primary.map()))throw new IllegalStateException("primary map provenance unavailable");
        Engine engine=new Engine(target,primary);
        for(Class<?> type=target.getClass();type!=null&&type!=LivingEntity.class;type=type.getSuperclass()){
            ClassNode node=DeepHealthProbe.classNode(type);
            for(MethodNode method:node.methods)if(Set.of("tick","m_8119_","baseTick","m_6075_","aiStep","m_8107_").contains(method.name)&&method.desc.equals("()V"))
                engine.analyze(new Method(type,method),V.object(target),List.of(),0);
        }
        if(engine.failure!=null)throw new IllegalStateException(engine.failure);
        List<Mirror> mirrors=new ArrayList<>();
        for(Read read:engine.copies){
            if(read.map.map==primary.map())continue;
            if(engine.unsafeCopies.stream().anyMatch(other->other.map.map==read.map.map&&other.key==read.key))continue;
            Mirror mirror=new Mirror(read.map,read.key,number(read.map.map.get(read.key)));
            if(Double.compare(mirror.current().doubleValue(),primary.value().doubleValue())!=0)throw new IllegalStateException("primary and proven mirror disagree before commit");
            if(mirrors.stream().noneMatch(old->old.ref.map==mirror.ref.map&&old.key==mirror.key))mirrors.add(mirror);
            if(mirrors.size()>8)throw new IllegalStateException("mirror count budget");
        }
        return new Plan(mirrors);
    }
    private static Number number(Object object){
        if(!(object instanceof Byte||object instanceof Short||object instanceof Integer||object instanceof Long||object instanceof Float||object instanceof Double)
            ||!Double.isFinite(((Number)object).doubleValue()))throw new IllegalStateException("unsupported mirror number");
        return (Number)object;
    }
    private static final class Engine {
        final LivingEntity target;final DeepHealthProbe.MapBinding primary;
        final List<Read> copies=new ArrayList<>(),unsafeCopies=new ArrayList<>();final Set<String> visited=new HashSet<>();
        final Map<String,V> results=new HashMap<>();
        int instructions,methods;String failure;
        Engine(LivingEntity target,DeepHealthProbe.MapBinding primary){this.target=target;this.primary=primary;}
        Class<?> load(Class<?> context,String name)throws Exception{return Class.forName(name.replace('/','.'),false,context.getClassLoader());}
        Method find(Class<?> type,String name,String descriptor)throws Exception{
            for(Class<?> owner=type;owner!=null&&!owner.getName().startsWith("java.");owner=owner.getSuperclass())
                for(MethodNode code:DeepHealthProbe.classNode(owner).methods)if(code.name.equals(name)&&code.desc.equals(descriptor))return new Method(owner,code);
            return null;
        }
        V mapField(Class<?> context,FieldInsnNode access,V receiver)throws Exception{
            Class<?> owner=load(context,access.owner);Field field=null;
            for(Class<?> type=owner;type!=null&&field==null;type=type.getSuperclass())try{field=type.getDeclaredField(access.name);}catch(NoSuchFieldException ignored){}
            if(field==null||!Map.class.isAssignableFrom(field.getType())||!field.trySetAccessible())return UNKNOWN;
            if(!Modifier.isStatic(field.getModifiers())&&receiver.object!=target)return UNKNOWN;
            Field selected=field;Object object=Modifier.isStatic(field.getModifiers())?null:target;
            Locator locator=()->selected.get(object);Object value=locator.get();
            return value instanceof Map<?,?> map&&MapWrite.safeRead(map)?V.object(new MapRef(map,locator)):UNKNOWN;
        }
        V analyze(Method method,V receiver,List<V> arguments,int depth)throws Exception{
            return analyze(method,receiver,arguments,depth,List.of());
        }
        V analyze(Method method,V receiver,List<V> arguments,int depth,List<Read> scope)throws Exception{
            if(method==null||depth>10||method.owner.getName().startsWith("net.minecraft."))return UNKNOWN;
            String signature=method.owner.getName()+method.code.name+method.code.desc+"/"+token(receiver);
            for(V argument:arguments)signature+="/"+token(argument);
            signature+="/guards:"+guardToken(scope);
            if(!visited.add(signature))return results.getOrDefault(signature,UNKNOWN);
            if(++methods>96)return UNKNOWN;
            AbstractInsnNode[] code=method.code.instructions.toArray();Map<LabelNode,Integer> labels=new HashMap<>();
            for(int i=0;i<code.length;i++)if(code[i] instanceof LabelNode label)labels.put(label,i);
            Map<Integer,V> initial=new HashMap<>();boolean stat=(method.code.access&Opcodes.ACC_STATIC)!=0;
            int local=stat?0:1;if(!stat)initial.put(0,receiver);Type[] types=Type.getArgumentTypes(method.code.desc);
            for(int i=0;i<arguments.size();i++){initial.put(local,arguments.get(i));local+=types[i].getSize();}
            Deque<State> pending=new ArrayDeque<>();pending.add(new State(0,initial,new ArrayList<>(),scope));
            Set<String> entered=new HashSet<>();List<V> returns=new ArrayList<>();int branches=0;
            while(!pending.isEmpty()&&branches++<96){
                State state=pending.removeFirst();String stateKey=state.pc+":"+guardToken(state.guards);
                for(var entry:new TreeMap<>(state.locals).entrySet())stateKey+="/"+entry.getKey()+"="+token(entry.getValue());
                for(V value:state.stack)stateKey+="/stack="+token(value);
                if(!entered.add(stateKey))continue;
                Map<Integer,V> locals=new HashMap<>(state.locals);List<V> stack=new ArrayList<>(state.stack);
                List<Read> guards=new ArrayList<>(state.guards);
                try{for(int pc=state.pc;pc<code.length;pc++){
                    AbstractInsnNode insn=code[pc];int op=insn.getOpcode();if(op<0)continue;
                    if(++instructions>24000)return UNKNOWN;
                    if(insn instanceof VarInsnNode variable){
                        if(op>=Opcodes.ILOAD&&op<=Opcodes.ALOAD){stack.add(locals.getOrDefault(variable.var,UNKNOWN));continue;}
                        if(op>=Opcodes.ISTORE&&op<=Opcodes.ASTORE){locals.put(variable.var,pop(stack));continue;}
                    }
                    if(insn instanceof LdcInsnNode constant){stack.add(V.object(constant.cst instanceof Number?constant.cst:null));continue;}
                    if(op>=Opcodes.ICONST_M1&&op<=Opcodes.ICONST_5){stack.add(V.object(op-Opcodes.ICONST_0));continue;}
                    if(op==Opcodes.LCONST_0||op==Opcodes.LCONST_1){stack.add(V.object((long)(op-Opcodes.LCONST_0)));continue;}
                    if(op>=Opcodes.FCONST_0&&op<=Opcodes.FCONST_2){stack.add(V.object((float)(op-Opcodes.FCONST_0)));continue;}
                    if(op==Opcodes.DCONST_0||op==Opcodes.DCONST_1){stack.add(V.object((double)(op-Opcodes.DCONST_0)));continue;}
                    if(insn instanceof IntInsnNode value&&(op==Opcodes.BIPUSH||op==Opcodes.SIPUSH)){stack.add(V.object(value.operand));continue;}
                    if(op==Opcodes.ACONST_NULL){stack.add(UNKNOWN);continue;}
                    if(op==Opcodes.NOP||op==Opcodes.CHECKCAST)continue;
                    if(op==Opcodes.DUP){V value=pop(stack);stack.add(value);stack.add(value);continue;}
                    if(op==Opcodes.POP){pop(stack);continue;}
                    if(insn instanceof JumpInsnNode jump){
                        Comparison comparison=null;
                        if(op!=Opcodes.GOTO){V condition=pop(stack);
                            if(op>=Opcodes.IF_ICMPEQ&&op<=Opcodes.IF_ICMPLE){comparison=new Comparison(pop(stack),condition,false);}
                            else if(op==Opcodes.IF_ACMPEQ||op==Opcodes.IF_ACMPNE)pop(stack);
                            else if(condition.object instanceof Comparison compared)comparison=compared;
                        }
                        List<Read> jumpGuards=new ArrayList<>(guards);Read integrity=comparison==null?null:integrity(comparison);
                        if(integrity!=null&&(op==Opcodes.IFEQ||op==Opcodes.IFNE||op==Opcodes.IF_ICMPEQ||op==Opcodes.IF_ICMPNE)){
                            boolean jumpMismatch=comparison.booleanEquality?(op==Opcodes.IFEQ):(op==Opcodes.IFNE||op==Opcodes.IF_ICMPNE);
                            if(jumpMismatch)jumpGuards.add(integrity);else guards.add(integrity);
                        }
                        Integer destination=labels.get(jump.label);
                        if(destination!=null&&destination>pc)pending.add(new State(destination,new HashMap<>(locals),new ArrayList<>(stack),jumpGuards));
                        if(op==Opcodes.GOTO)break;continue;
                    }
                    if(op>=Opcodes.LCMP&&op<=Opcodes.DCMPG){V b=pop(stack),a=pop(stack);stack.add(V.object(new Comparison(a,b,false)));continue;}
                    if(op>=Opcodes.IADD&&op<=Opcodes.DDIV){V b=pop(stack),a=pop(stack);stack.add(identityArithmetic(a,b,op));continue;}
                    if(op>=Opcodes.INEG&&op<=Opcodes.DNEG){pop(stack);stack.add(UNKNOWN);continue;}
                    if(op>=Opcodes.I2L&&op<=Opcodes.I2S){V value=pop(stack);stack.add(op==Opcodes.F2D?value:UNKNOWN);continue;}
                    if(insn instanceof FieldInsnNode field){
                        if(op==Opcodes.GETSTATIC){stack.add(mapField(method.owner,field,UNKNOWN));continue;}
                        if(op==Opcodes.GETFIELD){stack.add(mapField(method.owner,field,pop(stack)));continue;}
                        if(op==Opcodes.PUTSTATIC){pop(stack);continue;}
                        if(op==Opcodes.PUTFIELD){pop(stack);pop(stack);continue;}
                    }
                    if(insn instanceof MethodInsnNode call){
                        List<V> args=new ArrayList<>();for(int i=0;i<Type.getArgumentTypes(call.desc).length;i++)args.add(0,pop(stack));
                        V object=op==Opcodes.INVOKESTATIC?UNKNOWN:pop(stack),result=call(method.owner,call,object,args,depth,guards);
                        if(Type.getReturnType(call.desc).getSort()!=Type.VOID)stack.add(result);continue;
                    }
                    if(insn instanceof InvokeDynamicInsnNode dynamic){
                        List<V> captured=new ArrayList<>();for(int i=0;i<Type.getArgumentTypes(dynamic.desc).length;i++)captured.add(0,pop(stack));
                        Handle body=null;if(dynamic.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory"))for(Object argument:dynamic.bsmArgs)if(argument instanceof Handle handle)body=handle;
                        stack.add(body==null?UNKNOWN:new V(null,null,new Callback(body,List.copyOf(captured),method.owner)));continue;
                    }
                    if(op==Opcodes.NEW){stack.add(UNKNOWN);continue;}
                    if(op==Opcodes.INSTANCEOF){pop(stack);stack.add(UNKNOWN);continue;}
                    if(op>=Opcodes.IRETURN&&op<=Opcodes.ARETURN){returns.add(pop(stack));break;}
                    if(op==Opcodes.RETURN||op==Opcodes.ATHROW)break;
                    if(insn instanceof IincInsnNode)continue;
                    break; // Unknown storage/control flow is never guessed.
                }}catch(IndexOutOfBoundsException|IllegalArgumentException ignored){}
            }
            V returned=UNKNOWN;
            if(!returns.isEmpty()){V first=returns.get(0);if(returns.stream().allMatch(value->token(value).equals(token(first))))returned=first;}
            results.put(signature,returned);return returned;
        }
        Read integrity(Comparison comparison){
            Read a=comparison.left.source,b=comparison.right.source;
            if(a==null||b==null||a.key!=primary.key()||b.key!=primary.key())return null;
            if(a.map.map==primary.map()&&b.map.map!=primary.map())return b;
            if(b.map.map==primary.map()&&a.map.map!=primary.map())return a;
            return null;
        }
        String guardToken(List<Read> guards){String result="";for(Read read:guards)result+=System.identityHashCode(read.map.map)+":"+System.identityHashCode(read.key)+",";return result;}
        String token(V value){
            if(value.object==target)return "SELF";
            if(value.source!=null)return "SOURCE:"+System.identityHashCode(value.source.map.map)+":"+System.identityHashCode(value.source.key);
            if(value.object instanceof MapRef ref)return "MAP:"+System.identityHashCode(ref.map);
            if(value.callback!=null)return "CALLBACK:"+value.callback.handle;
            if(value.object instanceof Comparison comparison)return "COMPARE:"+token(comparison.left)+":"+token(comparison.right)+":"+comparison.booleanEquality;
            return value.object instanceof Number?"NUMBER:"+value.object:"UNKNOWN";
        }
        V call(Class<?> context,MethodInsnNode call,V object,List<V> args,int depth,List<Read> guards)throws Exception{
            String owner=call.owner,name=call.name;Type result=Type.getReturnType(call.desc);
            if(owner.equals("cn/fusionarsenal/CreativeRuntimeHooks")&&name.equals("health")&&call.desc.equals("(Ljava/lang/Object;F)F"))return args.get(1);
            if(object.callback!=null&&name.equals("run")&&args.isEmpty()){
                Callback callback=object.callback;Handle handle=callback.handle;Class<?> declared=load(callback.context,handle.getOwner());
                List<V> captured=new ArrayList<>(callback.captured);V receiver=handle.getTag()==Opcodes.H_INVOKESTATIC?UNKNOWN:captured.remove(0);
                if(receiver.object==target||captured.stream().anyMatch(v->v.object==target))return analyze(find(declared,handle.getName(),handle.getDesc()),receiver,captured,depth+1,guards);
                return UNKNOWN;
            }
            if(object.object instanceof MapRef ref){
                if((name.equals("get")||name.equals("getOrDefault"))&&!args.isEmpty()&&args.get(0).object==primary.key()){
                    Object key=args.get(0).object;
                    if(ref.map.containsKey(key)){Object value=ref.map.get(key);try{return V.read(number(value),new Read(ref,key));}catch(IllegalStateException ignored){}}
                    return V.read(null,new Read(ref,key)); // Missing required mirrors reject preparation, never become silent one-map writes.
                }
                if((name.equals("put")||name.equals("replace"))&&args.size()==2&&ref.map==primary.map()&&args.get(0).object==primary.key()&&args.get(1).source!=null){
                    Read source=args.get(1).source;
                    if(source.key==primary.key()&&source.map.map!=primary.map()){
                        boolean guarded=guards.stream().anyMatch(guard->guard.map.map==source.map.map&&guard.key==source.key);
                        (guarded?copies:unsafeCopies).add(source);
                    }
                }
                return UNKNOWN;
            }
            if((owner.equals("java/lang/Float")||owner.equals("java/lang/Double")||owner.equals("java/lang/Integer")||owner.equals("java/lang/Long"))){
                if(name.equals("valueOf")&&args.size()==1)return args.get(0);
                if((name.endsWith("Value"))&&args.isEmpty())return object;
                if(name.equals("equals")&&args.size()==1)return V.object(new Comparison(object,args.get(0),true));
                if(name.equals("compare")&&args.size()==2)return V.object(new Comparison(args.get(0),args.get(1),false));
            }
            if(owner.equals("java/util/Objects")&&name.equals("equals")&&args.size()==2)return V.object(new Comparison(args.get(0),args.get(1),true));
            if((owner.equals("net/minecraft/util/Mth")&&(name.equals("clamp")||name.equals("m_14036_")))&&args.size()==3){
                V input=args.get(0),low=args.get(1),high=args.get(2);
                if(input.source!=null&&low.object instanceof Number a&&high.object instanceof Number b&&a.doubleValue()<=0&&b.doubleValue()>=primary.value().doubleValue())return input;
                return UNKNOWN;
            }
            if((owner.equals("java/lang/Math")||owner.equals("net/minecraft/util/Mth"))&&args.size()==2&&(name.equals("min")||name.equals("max"))){
                V source=args.get(0).source!=null?args.get(0):args.get(1),bound=source==args.get(0)?args.get(1):args.get(0);
                if(source.source!=null&&bound.object instanceof Number n&&(name.equals("min")?n.doubleValue()>=primary.value().doubleValue():n.doubleValue()<=0))return source;
                return UNKNOWN;
            }
            boolean sensitive=object.object==target||args.stream().anyMatch(v->v.object==target||v.source!=null||v.callback!=null);
            if(!sensitive&&result.getSort()!=Type.OBJECT)return UNKNOWN;
            Class<?> declared=load(context,owner);
            if((result.getSort()==Type.OBJECT&&Map.class.isAssignableFrom(load(context,result.getInternalName())))
                ||object.object==target&&args.isEmpty()&&(name.equals("getMaxHealth")||name.equals("m_21233_"))){
                try{
                    List<Object> values=new ArrayList<>();for(V argument:args)values.add(argument.object);
                    Object receiver=object.object;Locator locator=()->DeepHealthProbe.pureCall(target,declared,name,call.desc,receiver,values,call.getOpcode());
                    Object value=locator.get();
                    if(value instanceof Map<?,?> map&&MapWrite.safeRead(map))return V.object(new MapRef(map,locator));
                    if(value instanceof Number)return V.object(value);
                }catch(Exception|LinkageError ignored){}
            }
            if(owner.startsWith("java/")||owner.startsWith("javax/")||owner.startsWith("net/minecraft/"))return UNKNOWN;
            if(sensitive){
                Class<?> dispatch=object.object==target&&call.getOpcode()!=Opcodes.INVOKESPECIAL?target.getClass():declared;
                return analyze(find(dispatch,name,call.desc),object,args,depth+1,guards);
            }
            return UNKNOWN;
        }
        V identityArithmetic(V a,V b,int opcode){
            if(b.object instanceof Number n&&((opcode>=Opcodes.IADD&&opcode<=Opcodes.DSUB)&&n.doubleValue()==0
                ||(opcode>=Opcodes.IMUL&&opcode<=Opcodes.DDIV)&&n.doubleValue()==1))return a;
            if(a.object instanceof Number n&&opcode>=Opcodes.IADD&&opcode<=Opcodes.DADD&&n.doubleValue()==0)return b;
            return UNKNOWN;
        }
        V pop(List<V> stack){return stack.remove(stack.size()-1);}
    }
}
