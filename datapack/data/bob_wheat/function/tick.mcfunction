execute as @a[nbt={SelectedItem:{id:"minecraft:wheat"}},tag=!wl_loving] run item modify entity @s weapon.mainhand bob_wheat:make_wheat_edible
execute as @a[nbt={Inventory:[{Slot:-106b,id:"minecraft:wheat"}]},tag=!wl_loving] run item modify entity @s weapon.offhand bob_wheat:make_wheat_edible
