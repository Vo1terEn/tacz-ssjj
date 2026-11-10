var O=Java.type('org.objectweb.asm.Opcodes');
var List=Java.type('org.objectweb.asm.tree.InsnList');
var Var=Java.type('org.objectweb.asm.tree.VarInsnNode');
var Call=Java.type('org.objectweb.asm.tree.MethodInsnNode');
var Jump=Java.type('org.objectweb.asm.tree.JumpInsnNode');
var Label=Java.type('org.objectweb.asm.tree.LabelNode');
var Insn=Java.type('org.objectweb.asm.tree.InsnNode');
var API='cn/ssjjparasite2/client/ParasiteCannon';
function shootQueries(node){
 var ammo=0,consume=0;
 for(var j=0;j<node.methods.size();j++){
  var m=node.methods.get(j);
  for(var it=m.instructions.iterator();it.hasNext();){var c=it.next();
   if(!(c instanceof Call)||c.owner!=='com/tacz/guns/api/item/IGun'||c.name!=='getCurrentAmmoCount'||c.desc!=='(Lnet/minecraft/world/item/ItemStack;)I')continue;
   c.setOpcode(O.INVOKESTATIC);c.owner=API;c.name='shootAmmo';c.desc='(Lcom/tacz/guns/api/item/IGun;Lnet/minecraft/world/item/ItemStack;)I';c.itf=false;ammo++;
  }
  for(var it=m.instructions.iterator();it.hasNext();){var c=it.next();
   if(!(c instanceof Call)||c.owner!=='com/tacz/guns/api/entity/IGunOperator'||c.name!=='consumesAmmoOrNot'||c.desc!=='()Z')continue;
   c.setOpcode(O.INVOKESTATIC);c.owner=API;c.name='consumeAmmo';c.desc='(Lcom/tacz/guns/api/entity/IGunOperator;)Z';c.itf=false;consume++;
  }
 }
 var local=node.name.endsWith('/LocalPlayerShoot');
 if(ammo!==(local?2:1)||consume!==(local?1:0))throw new Error('Unsupported Parasite shoot query layout: '+node.name+' '+ammo+'/'+consume);
 return node;
}
function freeAmmo(node){
 var changed=0;
 for(var j=0;j<node.methods.size();j++){
  var m=node.methods.get(j);if(m.desc!=='()Z'||(m.name!=='reduceAmmoOnce'&&m.name!=='isShootingNeedConsumeAmmo'))continue;
  var p=new List(),pass=new Label();p.add(new Var(O.ALOAD,0));p.add(new Call(O.INVOKESTATIC,API,'freeShot','(Lcom/tacz/guns/item/ModernKineticGunScriptAPI;)Z',false));p.add(new Jump(O.IFEQ,pass));p.add(new Insn(m.name==='reduceAmmoOnce'?O.ICONST_1:O.ICONST_0));p.add(new Insn(O.IRETURN));p.add(pass);m.instructions.insert(p);m.maxStack=Math.max(m.maxStack,1);changed++;
 }
 if(changed!==2)throw new Error('Unsupported Parasite ammo API');return node;
}
function damage(node){
 var changed=0;
 for(var j=0;j<node.methods.size();j++){
  var m=node.methods.get(j);if(m.name!=='getDamage'||m.desc!=='(Lnet/minecraft/world/phys/Vec3;)F')continue;
  for(var it=m.instructions.iterator();it.hasNext();){var c=it.next();
   if(!(c instanceof Call)||c.owner!=='com/tacz/guns/resource/pojo/data/gun/ExtraDamage$DistanceDamagePair'||c.name!=='getDamage'||c.desc!=='()F')continue;
   m.instructions.insertBefore(c,new Var(O.ALOAD,0));c.setOpcode(O.INVOKESTATIC);c.owner=API;c.name='distanceDamage';c.desc='(Lcom/tacz/guns/resource/pojo/data/gun/ExtraDamage$DistanceDamagePair;Lcom/tacz/guns/entity/EntityKineticBullet;)F';c.itf=false;changed++;
  }m.maxStack+=1;
 }
 if(changed!==1)throw new Error('Unsupported Parasite bullet damage layout');return node;
}
function interval(node){
 var changed=0;
 for(var j=0;j<node.methods.size();j++){
  var m=node.methods.get(j);if(m.name!=='getShootInterval'||m.desc!=='(Lnet/minecraft/world/entity/LivingEntity;Lcom/tacz/guns/api/item/gun/FireMode;Lnet/minecraft/world/item/ItemStack;)J')continue;
  for(var it=m.instructions.iterator();it.hasNext();){var c=it.next();if(c.getOpcode()!==O.LRETURN)continue;
   var p=new List();p.add(new Var(O.ALOAD,3));p.add(new Call(O.INVOKESTATIC,API,'shotInterval','(JLnet/minecraft/world/item/ItemStack;)J',false));m.instructions.insertBefore(c,p);changed++;
  }m.maxStack+=1;
 }
 if(changed!==1)throw new Error('Unsupported Parasite shot interval layout');return node;
}
function initializeCoreMod(){return {
 parasite_client_ammo:{target:{type:'CLASS',name:'com.tacz.guns.client.gameplay.LocalPlayerShoot'},transformer:shootQueries},
 parasite_server_ammo:{target:{type:'CLASS',name:'com.tacz.guns.entity.shooter.LivingEntityShoot'},transformer:shootQueries},
 parasite_free_ammo:{target:{type:'CLASS',name:'com.tacz.guns.item.ModernKineticGunScriptAPI'},transformer:freeAmmo},
 parasite_cannon_damage:{target:{type:'CLASS',name:'com.tacz.guns.entity.EntityKineticBullet'},transformer:damage},
 parasite_cannon_interval:{target:{type:'CLASS',name:'com.tacz.guns.resource.pojo.data.gun.GunData'},transformer:interval}
};}
