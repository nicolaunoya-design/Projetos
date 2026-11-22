#!/usr/bin/env python3
"""
Versão COLORIDA da polia (mesma mesa de impressão do polia_mesa_completa.stl).

Gera em stl/polia/cores/:
  polia_colorida.3mf      cores por peça (régua e títulos em azul-escuro)
  polia_colorida.obj/.mtl cores por peça, para fatiadores que leem OBJ colorido
  polia_1_branco.stl ...  uma mesa por cor, para impressora de uma cor só
                          (imprime uma, troca o filamento, imprime a próxima)

Uso:  python3 gerar_cores.py
"""
import os
import zipfile

import numpy as np

import gerar_modelos as g

AQUI = os.path.dirname(os.path.abspath(__file__))
PASTA = os.path.join(AQUI, "stl", "polia", "cores")

CORES = {  # nome, cor (RGB hex)
    "branco": ("Branco", "#EEF0F2"),
    "marca": ("Azul-escuro", "#22314F"),
    "azul": ("Azul", "#2563C9"),
    "vermelho": ("Vermelho", "#D8402F"),
    "amarelo": ("Amarelo", "#F4B400"),
}


def no_chao_par(corpo, marcas, x, y, rot=0.0):
    """Como gerar_modelos.no_chao, mas move corpo e marcas juntos."""
    tudo = corpo + marcas
    if rot:
        tudo, corpo, marcas = (m.rotate((0, 0, rot)) for m in (tudo, corpo, marcas))
    x0, y0, z0, x1, y1, _ = tudo.bounding_box()
    d = (x - (x0 + x1) / 2, y - (y0 + y1) / 2, -z0)
    return corpo.translate(d), marcas.translate(d)


def pecas_coloridas(h=g.H_PADRAO):
    """Peças na mesma posição da mesa completa: [(nome, cor, sólido)]."""
    portico, regua = no_chao_par(*g.portico(h, separado=True), 75, h / 2)
    placa, titulo = no_chao_par(*g.placa_titulo(separado=True), 124, 79, rot=90)
    # as marcas ficam 0,2 mm dentro do corpo: tira essa parte do corpo
    pecas = [("portico", "branco", portico - regua), ("portico_regua", "marca", regua),
             ("placa", "branco", placa - titulo), ("placa_titulo", "marca", titulo)]
    itens = g.itens_mesa_polia(h)
    nomes = ["pe_1", "pe_2", "garfo_1", "garfo_2", "balde_1", "balde_2", "roldana_1", "roldana_2"]
    cores = ["branco", "branco", "azul", "azul", "amarelo", "amarelo", "vermelho", "vermelho"]
    pecas += [(n, c, m) for n, c, m in zip(nomes, cores, itens[2:])]
    return pecas


def malha(m):
    mesh = m.to_mesh()
    return np.asarray(mesh.vert_properties)[:, :3], np.asarray(mesh.tri_verts)


def salvar_3mf(pecas, caminho):
    ordem = list(CORES)
    mats = "".join(f'<base name="{CORES[c][0]}" displaycolor="{CORES[c][1]}FF"/>' for c in ordem)
    objs, itens = [], []
    for i, (nome, cor, m) in enumerate(pecas, start=2):
        v, t = malha(m)
        vs = "".join(f'<vertex x="{a:.4f}" y="{b:.4f}" z="{c:.4f}"/>' for a, b, c in v)
        ts = "".join(f'<triangle v1="{a}" v2="{b}" v3="{c}"/>' for a, b, c in t)
        objs.append(f'<object id="{i}" name="polia_{nome}" type="model" pid="1" '
                    f'pindex="{ordem.index(cor)}"><mesh><vertices>{vs}</vertices>'
                    f'<triangles>{ts}</triangles></mesh></object>')
        itens.append(f'<item objectid="{i}"/>')
    modelo = ('<?xml version="1.0" encoding="UTF-8"?>\n'
              '<model unit="millimeter" xml:lang="pt-BR" '
              'xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">'
              '<metadata name="Title">Polia fixa + polia móvel</metadata>'
              f'<resources><basematerials id="1">{mats}</basematerials>{"".join(objs)}</resources>'
              f'<build>{"".join(itens)}</build></model>')
    tipos = ('<?xml version="1.0" encoding="UTF-8"?>\n'
             '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
             '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
             '<Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml"/>'
             '</Types>')
    rels = ('<?xml version="1.0" encoding="UTF-8"?>\n'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Target="/3D/3dmodel.model" Id="rel0" '
            'Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/></Relationships>')
    with zipfile.ZipFile(caminho, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", tipos)
        z.writestr("_rels/.rels", rels)
        z.writestr("3D/3dmodel.model", modelo)


def salvar_obj(pecas, caminho):
    base = os.path.splitext(caminho)[0]
    with open(base + ".mtl", "w", encoding="utf-8") as f:
        for c, (nome, hexa) in CORES.items():
            r, gg, b = (int(hexa[i:i + 2], 16) / 255 for i in (1, 3, 5))
            f.write(f"newmtl {c}\nKd {r:.4f} {gg:.4f} {b:.4f}\n\n")
    with open(caminho, "w", encoding="utf-8") as f:
        f.write(f"mtllib {os.path.basename(base)}.mtl\n")
        n0 = 1
        for nome, cor, m in pecas:
            v, t = malha(m)
            f.write(f"o polia_{nome}\nusemtl {cor}\n")
            f.writelines(f"v {a:.4f} {b:.4f} {c:.4f}\n" for a, b, c in v)
            f.writelines(f"f {a + n0} {b + n0} {c + n0}\n" for a, b, c in t)
            n0 += len(v)


def main():
    os.makedirs(PASTA, exist_ok=True)
    pecas = pecas_coloridas()
    salvar_3mf(pecas, os.path.join(PASTA, "polia_colorida.3mf"))
    salvar_obj(pecas, os.path.join(PASTA, "polia_colorida.obj"))
    # uma mesa por cor (a régua e o título saem em relevo na peça branca)
    grupos = [("1_branco", ["branco", "marca"]), ("2_azul", ["azul"]),
              ("3_vermelho", ["vermelho"]), ("4_amarelo", ["amarelo"])]
    for nome, cores in grupos:
        m = g.uniao([p for _, c, p in pecas if c in cores])
        g.para_trimesh(m).export(os.path.join(PASTA, f"polia_{nome}.stl"))
        print(f"polia_{nome}.stl  {m.volume() / 1000:.1f} cm³")
    print("polia_colorida.3mf e polia_colorida.obj gerados em", PASTA)


if __name__ == "__main__":
    main()
