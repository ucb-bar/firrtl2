---

![FIRRTL](https://raw.githubusercontent.com/freechipsproject/firrtl/master/doc/images/firrtl_logo.svg?sanitize=true)

---

![Build Status](https://github.com/ucb-bar/firrtl2/workflows/Continuous%20Integration/badge.svg)

### Flexible Internal Representation for RTL

 Firrtl is an intermediate representation (IR) for digital circuits designed as a platform for writing circuit-level transformations.
 This repository consists of a collection of transformations (written in Scala) which simplify, verify, transform, or emit their input circuit.

 A Firrtl compiler is constructed by chaining together these transformations, then writing the final circuit to a file.

 For a detailed description of Firrtl's intermediate representation, see the [FIRRTL Language Specification](https://github.com/chipsalliance/firrtl-spec/releases/latest/download/spec.pdf) ([source](https://github.com/chipsalliance/firrtl-spec)).


### Citing Firrtl

If you use Firrtl in a paper, please cite the following ICCAD paper and technical report:
https://ieeexplore.ieee.org/document/8203780
```
@INPROCEEDINGS{8203780, 
author={A. Izraelevitz and J. Koenig and P. Li and R. Lin and A. Wang and A. Magyar and D. Kim and C. Schmidt and C. Markley and J. Lawson and J. Bachrach}, 
booktitle={2017 IEEE/ACM International Conference on Computer-Aided Design (ICCAD)}, 
title={Reusability is FIRRTL ground: Hardware construction languages, compiler frameworks, and transformations}, 
year={2017}, 
volume={}, 
number={}, 
pages={209-216}, 
keywords={field programmable gate arrays;hardware description languages;program compilers;software reusability;hardware development practices;hardware libraries;open-source hardware intermediate representation;hardware compiler transformations;Hardware construction languages;retargetable compilers;software development;virtual Cambrian explosion;hardware compiler frameworks;parameterized libraries;FIRRTL;FPGA mappings;Chisel;Flexible Intermediate Representation for RTL;Reusability;Hardware;Libraries;Hardware design languages;Field programmable gate arrays;Tools;Open source software;RTL;Design;FPGA;ASIC;Hardware;Modeling;Reusability;Hardware Design Language;Hardware Construction Language;Intermediate Representation;Compiler;Transformations;Chisel;FIRRTL}, 
doi={10.1109/ICCAD.2017.8203780}, 
ISSN={1558-2434}, 
month={Nov},}
```

https://www2.eecs.berkeley.edu/Pubs/TechRpts/2016/EECS-2016-9.html
```
@techreport{Li:EECS-2016-9,
    Author = {Li, Patrick S. and Izraelevitz, Adam M. and Bachrach, Jonathan},
    Title = {Specification for the FIRRTL Language},
    Institution = {EECS Department, University of California, Berkeley},
    Year = {2016},
    Month = {Feb},
    URL = {http://www2.eecs.berkeley.edu/Pubs/TechRpts/2016/EECS-2016-9.html},
    Number = {UCB/EECS-2016-9}
}
```
