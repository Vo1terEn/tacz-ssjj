import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import com.google.gson.*;

/** Precise lifecycle/annotation overlay; existing gameplay instructions are preserved. */
public final class UnifyDarkLifecycle {
    private static final String SUBSCRIBER = "Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;";
    private static final String MOD = "Lnet/minecraftforge/fml/common/Mod;";
    private static final String MAIN_ID = "eye_judgment_arsenal";
    private static final String OLD_LOG = "[DarkBladeFX] Shader listener wired to ssjj_dark mod bus";
    private static final String NEW_LOG = "[DarkBladeFX] Shader listener wired to eye_judgment_arsenal mod bus";

    public static void main(String[] args) throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("source.jar overlay-directory evidence.json");
        Path output=Path.of(args[1]);Files.createDirectories(output);
        var edits=new ArrayList<Map<String,Object>>();int owners=0,containers=0,configs=0,logs=0;
        try(var jar=new JarFile(args[0])) {
            for(var entry:Collections.list(jar.entries())) {
                if(!entry.getName().startsWith("ssjj/dark/") || !entry.getName().endsWith(".class"))continue;
                ClassNode node=new ClassNode();new ClassReader(jar.getInputStream(entry).readAllBytes()).accept(node,0);
                int ownerEdits=0,containerEdits=0,configEdits=0,logEdits=0;
                for(var annotations:Arrays.asList(node.visibleAnnotations,node.invisibleAnnotations)) {
                    if(annotations==null)continue;
                    var iter=annotations.iterator();while(iter.hasNext()) {
                        var a=iter.next();
                        if(a.desc.equals(MOD)) {
                            if(!node.name.equals("ssjj/dark/DarkCrystalMod"))throw new AssertionError("Unexpected dark container "+node.name);
                            iter.remove();containerEdits++;continue;
                        }
                        if(!a.desc.equals(SUBSCRIBER))continue;
                        boolean found=false;
                        for(int i=0;i<a.values.size();i+=2)if(a.values.get(i).equals("modid")) {
                            if(!a.values.get(i+1).equals("ssjj_dark"))throw new AssertionError("Unexpected old subscriber owner "+node.name);
                            a.values.set(i+1,MAIN_ID);found=true;ownerEdits++;
                        }
                        if(!found)throw new AssertionError("Missing explicit subscriber owner "+node.name);
                    }
                }
                for(var method:node.methods)for(var insn:method.instructions.toArray()) {
                    if(node.name.equals("ssjj/dark/DarkCrystalMod") && method.name.equals("<init>") && insn instanceof MethodInsnNode call && call.owner.equals("net/minecraftforge/fml/ModLoadingContext") && call.name.equals("registerConfig")) {
                        if(!call.desc.equals("(Lnet/minecraftforge/fml/config/ModConfig$Type;Lnet/minecraftforge/fml/config/IConfigSpec;)V"))throw new AssertionError("Unexpected config registration signature");
                        method.instructions.insertBefore(call,new LdcInsnNode("ssjj_dark-server.toml"));
                        call.desc="(Lnet/minecraftforge/fml/config/ModConfig$Type;Lnet/minecraftforge/fml/config/IConfigSpec;Ljava/lang/String;)V";configEdits++;
                    }
                    if(node.name.equals("ssjj/dark/DarkClientBootstrap") && insn instanceof LdcInsnNode ldc && OLD_LOG.equals(ldc.cst)) {ldc.cst=NEW_LOG;logEdits++;}
                }
                if(ownerEdits+containerEdits+configEdits+logEdits==0)continue;
                // The config filename adds one stack slot; frames and control flow do not change.
                ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);
                Path target=output.resolve(entry.getName());Files.createDirectories(target.getParent());Files.write(target,writer.toByteArray());
                owners+=ownerEdits;containers+=containerEdits;configs+=configEdits;logs+=logEdits;
                edits.add(Map.of("class",entry.getName(),"subscriber_owner_changes",ownerEdits,"removed_mod_annotations",containerEdits,"explicit_legacy_config",configEdits,"bus_log_changes",logEdits));
            }
        }
        if(owners!=13 || containers!=1 || configs!=1 || logs!=1)throw new AssertionError("Unexpected lifecycle patch scope: "+owners+"/"+containers+"/"+configs+"/"+logs);
        var result=new LinkedHashMap<String,Object>();result.put("subscriber_owner_changes",owners);result.put("removed_mod_annotations",containers);result.put("explicit_legacy_config",configs);result.put("bus_log_changes",logs);result.put("edits",edits);
        Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        System.out.println("Dark module lifecycle: 13 subscriber owners, 1 removed container, 1 explicit legacy config, 1 bus log.");
    }
}
