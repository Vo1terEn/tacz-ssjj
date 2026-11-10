var O=Java.type('org.objectweb.asm.Opcodes');
var Insn=Java.type('org.objectweb.asm.tree.InsnNode');
var Var=Java.type('org.objectweb.asm.tree.VarInsnNode');
var Call=Java.type('org.objectweb.asm.tree.MethodInsnNode');
var Jump=Java.type('org.objectweb.asm.tree.JumpInsnNode');
var Label=Java.type('org.objectweb.asm.tree.LabelNode');
var List=Java.type('org.objectweb.asm.tree.InsnList');
var Frame=Java.type('org.objectweb.asm.tree.FrameNode');
var BRIDGE='cn/ssjjcompat/ItemPresentationCompat';
function hook(node,descriptor,name,arguments,bridgeDescriptor){
    var matches=[];
    for(var i=0;i<node.methods.size();i++){
        var method=node.methods.get(i);if(method.desc!==descriptor)continue;
        if(name==='workbench'&&method.name!=='renderLeftModel')continue;
        var old=method.instructions.toArray();
        for(var j=0;j<old.length;j++)if(old[j] instanceof Call&&old[j].owner===BRIDGE&&old[j].name===name)return node;
        matches.push(method);
    }
    if(matches.length!==1)throw new Error('SSJJRPG: unsupported '+name+' item presentation layout: '+matches.length);
    var added=new List();
    for(var i=0;i<arguments.length;i++)added.add(new Var(arguments[i][0],arguments[i][1]));
    added.add(new Call(O.INVOKESTATIC,BRIDGE,name,bridgeDescriptor,false));
    var resume=new Label();added.add(new Jump(O.IFEQ,resume));added.add(new Insn(O.RETURN));added.add(resume);added.add(new Frame(O.F_SAME,0,null,0,null));
    matches[0].instructions.insert(added);matches[0].maxStack=Math.max(matches[0].maxStack,arguments.length);return node;
}
function item(node){return hook(node,
 '(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V',
 'render',[[O.ALOAD,0],[O.ALOAD,1],[O.ALOAD,2],[O.ILOAD,3],[O.ALOAD,4],[O.ALOAD,5],[O.ILOAD,6],[O.ILOAD,7],[O.ALOAD,8]],
 '(Lnet/minecraft/client/renderer/entity/ItemRenderer;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)Z');}
function workbench(node){
 var matches=0;
 var descriptor='(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;IILcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;I)V';
 for(var i=0;i<node.methods.size();i++){
  var m=node.methods.get(i);if(m.name!=='renderLeftModel'||m.desc!=='(Lcom/tacz/guns/crafting/GunSmithTableRecipe;)V')continue;
  var instructions=m.instructions.toArray();
  for(var j=0;j<instructions.length;j++){
   var call=instructions[j];if(!(call instanceof Call))continue;
   if(call.owner===BRIDGE&&call.name==='workbenchRender')return node;
   if(call.owner==='net/minecraft/client/renderer/entity/ItemRenderer'&&call.desc===descriptor){
    m.instructions.set(call,new Call(O.INVOKESTATIC,BRIDGE,'workbenchRender','(Lnet/minecraft/client/renderer/entity/ItemRenderer;'+descriptor.substring(1),false));matches++;
   }
  }
 }
 if(matches!==1)throw new Error('SSJJRPG: unsupported native rotating preview: '+matches);
 return node;
}
function initializeCoreMod(){return {
 'ssjj_item_contexts':{target:{type:'CLASS',name:'net.minecraft.client.renderer.entity.ItemRenderer'},transformer:item},
 'ssjj_smithing_preview':{target:{type:'CLASS',name:'com.tacz.guns.client.gui.GunSmithTableScreen'},transformer:workbench}
};}
