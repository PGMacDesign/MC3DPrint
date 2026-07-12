"""Entry point: python3 -m tools.mc_texgen ..."""
import sys

from .cli import main

if __name__ == "__main__":
    sys.exit(main())
