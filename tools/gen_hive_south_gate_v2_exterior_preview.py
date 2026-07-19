#!/usr/bin/env python3
from pathlib import Path
import runpy, sys
ROOT=Path(__file__).resolve().parents[1]
TOOLS=Path(__file__).resolve().parent
sys.path.insert(0,str(TOOLS))
from hive_module_lib import ModuleBuilder
ns=runpy.run_path(str(TOOLS/'gen_hive_south_gate_v2.py'))
src=ns['b']
b=ModuleBuilder(src.sx,src.sy,src.sz,seed=1)
for (x,y,z),state in src.grid.items():
    b.put(src.sx-1-x,y,src.sz-1-z,state)
