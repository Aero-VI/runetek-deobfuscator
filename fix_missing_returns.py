#!/usr/bin/env python3
"""Fix missing return statements in Vineflower-decompiled Java source files.

For methods that Vineflower couldn't decompile (marked with $VF: Couldn't be decompiled),
the method body is just bytecode comments with no return statement. This script adds
appropriate return stubs.
"""
import re, os, glob, sys

src_dir = sys.argv[1] if len(sys.argv) > 1 else "output/508/src"

for filepath in sorted(glob.glob(os.path.join(src_dir, "*.java"))):
    with open(filepath, 'r') as f:
        content = f.read()
    
    if "$VF: Couldn't be decompiled" not in content:
        continue
    
    lines = content.split('\n')
    new_lines = []
    i = 0
    fixed = 0
    
    while i < len(lines):
        line = lines[i]
        new_lines.append(line)
        
        # Detect start of a failed decompilation block
        if "$VF: Couldn't be decompiled" in line:
            # Walk backward through new_lines to find the method signature
            method_sig = ""
            for j in range(len(new_lines) - 1, max(0, len(new_lines) - 15), -1):
                method_sig = new_lines[j] + "\n" + method_sig
                if re.search(r'\b(void|boolean|int|long|float|double|byte|short|char|String|Object|Class_\w+(?:\[\])*)\s+\w+\s*\(', new_lines[j]):
                    break
            
            # Determine return type
            return_type = 'void'
            m = re.search(r'\b(boolean|int|long|float|double|byte|short|char|String|Object|Class_\w+(?:\[\])*)\s+\w+\s*\(', method_sig)
            if m:
                return_type = m.group(1)
            
            # Now scan forward to find the closing brace of this method
            # The pattern is: bytecode comments, then closing brace "   }"
            j = i + 1
            while j < len(lines):
                new_lines.append(lines[j])
                # The closing brace of the method (indented 3 spaces)
                if lines[j].strip() == '}' and not lines[j].startswith('      '):
                    # Add return before this closing brace
                    brace_line = new_lines.pop()  # remove the brace temporarily
                    
                    # Generate appropriate return
                    if return_type == 'void':
                        stub = '      return; // stub: method could not be decompiled'
                    elif return_type == 'boolean':
                        stub = '      return false; // stub: method could not be decompiled'
                    elif return_type in ('int', 'long', 'float', 'double', 'byte', 'short', 'char'):
                        stub = '      return 0; // stub: method could not be decompiled'
                    else:
                        stub = '      return null; // stub: method could not be decompiled'
                    
                    new_lines.append(stub)
                    new_lines.append(brace_line)
                    fixed += 1
                    fname = os.path.basename(filepath)
                    print(f"  {fname}: Added {return_type} return stub (line ~{j})")
                    break
                j += 1
            i = j + 1
            continue
        
        i += 1
    
    if fixed > 0:
        with open(filepath, 'w') as f:
            f.write('\n'.join(new_lines))
        print(f"  Fixed {fixed} methods in {os.path.basename(filepath)}")

print("Done")
