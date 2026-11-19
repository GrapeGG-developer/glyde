import os
here = os.path.dirname(os.path.abspath(__file__))
out = os.path.join(here, "..", "..", "app", "src", "main", "java", "gpr", "greentune", "unblock", "ui", "aero", "Shaders.kt")
parts = ["package gpr.greentune.unblock.ui.aero\n\nobject Shaders {\n"]
for name, const in (("orb.agsl", "ORB"), ("bg.agsl", "BACKGROUND")):
    src = open(os.path.join(here, name)).read()
    assert "$" not in src and '"""' not in src
    parts.append(f'    const val {const} = """\n{src}"""\n\n')
parts[-1] = parts[-1].rstrip("\n") + "\n"
parts.append("}\n")
open(out, "w").write("".join(parts))
print(out)
