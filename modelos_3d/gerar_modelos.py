#!/usr/bin/env python3
"""
Modelos 3D para impressão: interior do ESPERMATOZOIDE e do ÓVULO (em corte).

Cada modelo é um "corte" didático: a face de cima é o plano de corte e as
organelas aparecem em relevo, como na ilustração de referência.

Saída (pasta stl/):
  - <modelo>_completo.stl  -> peça única, para imprimir em 1 cor e pintar
  - <modelo>/NN_<parte>.stl -> uma peça por cor, para impressora multicor
                               (importe todas juntas como "um objeto com
                               várias partes" no fatiador)

Uso:  python3 gerar_modelos.py            (tamanho padrão: 80 mm)
      python3 gerar_modelos.py --escala 1.25   (100 mm)

Requer: pip install manifold3d trimesh numpy scipy matplotlib
"""
import argparse
import json
import math
import os

import numpy as np
from manifold3d import CrossSection, FillRule, Manifold, Mesh, OpType
from scipy.interpolate import PchipInterpolator
import trimesh

AQUI = os.path.dirname(os.path.abspath(__file__))
FONTE = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"


# ---------------------------------------------------------------- utilitários

def segs(r, passo=0.45, minimo=16, maximo=200):
    """Número de segmentos para uma esfera de raio r (facetas de ~0.45 mm)."""
    return int(max(minimo, min(maximo, 2 * math.pi * r / passo)))


def esfera(r, c=(0, 0, 0), n=None):
    return Manifold.sphere(r, n or segs(r)).translate(tuple(c))


def elipsoide(a, b, c, centro=(0, 0, 0), ang=0.0, n=None):
    return (Manifold.sphere(1.0, n or segs(max(a, b, c)))
            .scale((a, b, c)).rotate((0, 0, ang)).translate(tuple(centro)))


def gota(r, c, n=28):
    """Esfera em forma de gota (ponta para baixo a 45°): imprime sem suporte."""
    pts = Manifold.sphere(r, n).to_mesh().vert_properties[:, :3] + np.asarray(c)
    ponta = np.asarray(c) + np.array([0, 0, -r * math.sqrt(2)])
    return Manifold.hull_points(np.vstack([pts, ponta]))


def uniao(lista):
    lista = [m for m in lista if not m.is_empty()]
    if not lista:
        return Manifold()
    return Manifold.batch_boolean(lista, OpType.Add)


def corrente(pontos, raios, n=20, escala_z=1.0):
    """Tubo suave: envoltória convexa entre esferas consecutivas."""
    base = Manifold.sphere(1.0, n).to_mesh().vert_properties[:, :3]
    base = base * np.array([1.0, 1.0, escala_z])
    nuvens = [base * r + np.asarray(p) for p, r in zip(pontos, raios)]
    gomos = [Manifold.hull_points(np.vstack([nuvens[i], nuvens[i + 1]]))
             for i in range(len(nuvens) - 1)]
    return uniao(gomos)


def acima(m, z):
    return m.trim_by_plane((0, 0, 1), z)


def abaixo(m, z):
    return m.trim_by_plane((0, 0, -1), -z)


def caixa(x0, x1, y0, y1, z0, z1):
    return Manifold.cube((x1 - x0, y1 - y0, z1 - z0)).translate((x0, y0, z0))


def texto(s, altura_maiuscula, x, y, z0, alto, alinhar="center"):
    """Texto em relevo (fonte Inter Bold), base em z0."""
    from matplotlib.font_manager import FontProperties
    from matplotlib.textpath import TextPath
    fp = FontProperties(fname=FONTE)
    ref = TextPath((0, 0), "H", size=100, prop=fp).get_extents()
    tamanho = 100 * altura_maiuscula / ref.height
    tp = TextPath((0, 0), s, size=tamanho, prop=fp)
    polys = [p for p in tp.to_polygons(closed_only=True) if len(p) >= 3]
    ext = tp.get_extents()
    dx = {"center": -(ext.x0 + ext.x1) / 2, "left": -ext.x0, "right": -ext.x1}[alinhar]
    cs = CrossSection([np.asarray(p) for p in polys], FillRule.EvenOdd)
    return Manifold.extrude(cs, alto).translate((x + dx, y, z0))


def retangulo_arredondado(x0, x1, y0, y1, r, n=12):
    pts = []
    for cx, cy, a0 in [(x1 - r, y0 + r, -90), (x1 - r, y1 - r, 0),
                       (x0 + r, y1 - r, 90), (x0 + r, y0 + r, 180)]:
        for k in range(n + 1):
            a = math.radians(a0 + 90 * k / n)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return np.array(pts)


def separar_por_prioridade(partes):
    """Remove sobreposições: quem vem primeiro na lista tem prioridade."""
    resultado, ocupado = [], Manifold()
    for nome, cor, m in partes:
        resultado.append((nome, cor, m if ocupado.is_empty() else m - ocupado))
        ocupado = ocupado + m
    return resultado


# ------------------------------------------------------------ espermatozoide
#
# Eixo X = comprimento (ponta da cabeça em x=0, fim da cauda em x=80).
# A peça fica deitada sobre uma base; a face de cima do corpo é o corte.

def perfil_espermatozoide():
    # meia-largura (mm) ao longo do comprimento
    largura = PchipInterpolator(*zip(*[
        (0.0, 0.40), (0.5, 1.40), (1.2, 2.30), (2.2, 3.10), (3.5, 3.85),
        (5.0, 4.50), (7.0, 5.15), (9.0, 5.60), (11.0, 5.80), (12.5, 5.70),
        (14.5, 5.15), (16.2, 4.20), (17.4, 3.10), (18.3, 2.10), (19.0, 1.60),
        (19.8, 1.50), (20.6, 1.60), (21.3, 2.70), (21.9, 3.30), (22.5, 3.40),
        (35.0, 3.40), (36.0, 3.10), (37.0, 2.45), (38.0, 2.20), (48.0, 1.85),
        (58.0, 1.45), (67.0, 1.10), (74.0, 0.80), (78.8, 0.50), (80.0, 0.28)]))
    # altura da face de corte acima da base (mm)
    altura = PchipInterpolator(*zip(*[
        (0.0, 4.0), (17.0, 4.0), (18.6, 3.2), (19.9, 3.2), (21.4, 3.8),
        (35.2, 3.8), (37.5, 3.1), (45.0, 2.6), (60.0, 1.9), (72.0, 1.4),
        (80.0, 1.0)]))

    def y_centro(x):
        t = np.clip((x - 37.0) / 43.0, 0, 1)
        rampa = np.clip((x - 37.0) / 11.0, 0, 1)
        rampa = rampa * rampa * (3 - 2 * rampa)
        return (1.6 + 3.6 * t) * np.sin(2 * np.pi * (x - 37.0) / 29.0) * rampa

    return largura, altura, y_centro


def secao_D(w, h, z_base=-0.3, n=9):
    """Seção em 'D': topo plano (corte) e laterais curvas que estreitam
    para baixo com no máximo ~45° de balanço (imprime sem suporte)."""
    w = max(w, 0.05)
    razao = h / w
    th = 1.30  # ângulo máximo (rad) da elipse lateral
    for cand in np.linspace(0.2, 1.30, 200):
        if math.sin(cand) ** 2 / math.cos(cand) > razao:
            th = cand
            break
    he = (h - z_base) / math.sin(th)
    pts = []
    for k in range(n + 1):
        a = th * k / n
        pts.append((w * math.cos(a), h - he * math.sin(a)))
    direita = pts
    esquerda = [(-p[0], p[1]) for p in reversed(pts)]
    return direita + esquerda  # (lateral, z)


def secao_ret(w, h, z_base):
    """Seção retangular de z_base até h (usada para escavar o interior)."""
    w = max(w, 0.05)
    return [(w, h), (w, z_base), (-w, z_base), (-w, h)]


def loft(xs, larg_fn, alt_fn, y_fn, extra_alt=0.0, encolher=0.0, z_base=-0.3,
         secao=secao_D):
    """Corpo varrido ao longo da linha central (malha única, sem emendas).
    z_base pode ser um número ou uma função de x."""
    def anel(x):
        y = float(y_fn(x))
        dy = float((y_fn(x + 0.05) - y_fn(x - 0.05)) / 0.1)
        t = np.array([1.0, dy]) / math.hypot(1.0, dy)
        nrm = np.array([-t[1], t[0]])
        w = max(float(larg_fn(x)) - encolher, 0.05)
        h = float(alt_fn(x)) + extra_alt
        zb = z_base(x) if callable(z_base) else z_base
        sec = secao(w, h, zb)
        return np.array([[x + s * nrm[0], y + s * nrm[1], z] for s, z in sec])
    aneis = [anel(x) for x in xs]
    K, N = len(aneis[0]), len(aneis)
    verts = np.vstack(aneis + [aneis[0].mean(0, keepdims=True),
                               aneis[-1].mean(0, keepdims=True)])
    c0, c1 = N * K, N * K + 1
    faces = []
    for i in range(N - 1):
        a, b = i * K, (i + 1) * K
        for k in range(K):
            k2 = (k + 1) % K
            faces += [(a + k, b + k, b + k2), (a + k, b + k2, a + k2)]
    for k in range(K):
        k2 = (k + 1) % K
        faces += [(c0, k, k2), (c1, (N - 1) * K + k2, (N - 1) * K + k)]
    faces = np.array(faces, dtype=np.uint32)
    m = Manifold(Mesh(vert_properties=verts.astype(np.float32), tri_verts=faces))
    if m.is_empty() or m.volume() < 0:
        m = Manifold(Mesh(vert_properties=verts.astype(np.float32),
                          tri_verts=faces[:, ::-1].copy()))
    assert not m.is_empty(), "loft inválido"
    return m


def ovo_elipsoide(cx, a, b, c, z0, afinar=0.10, y0=0.0):
    """Elipsoide em forma de ovo (mais estreito na frente, x menor)."""
    m = Manifold.sphere(1.0, 96).scale((a, b, c))

    def w(p):
        x, y, z = p
        f = 1.0 + afinar * (x / a)
        return (x + cx, y * f + y0, z + z0)
    return m.warp(w)


def espermatozoide():
    largura, altura, y_c = perfil_espermatozoide()
    X_FIM = 80.0
    PLACA = 2.4

    # ---- corpo inteiro numa só varredura; a membrana plasmática fica como
    #      uma borda em relevo (0,6 mm) em volta da cabeça e da peça intermediária
    def alt_borda(x):
        f = float(np.clip((37.0 - x) / 0.6, 0, 1))
        return float(altura(x)) + 0.6 * f * f * (3 - 2 * f)
    xs = np.concatenate([np.arange(0.0, X_FIM, 0.2), [X_FIM]])
    corpo = loft(xs, largura, alt_borda, y_c)
    interior = loft(np.arange(-0.4, 37.05, 0.2), largura,
                    lambda x: float(altura(x)) + 3.0, y_c, encolher=0.8,
                    z_base=lambda x: float(altura(x)) - 0.05, secao=secao_ret)
    corpo = corpo - interior
    X_CAUDA = 37.4
    cauda = corpo.trim_by_plane((1, 0, 0), X_CAUDA)        # bainha da cauda
    corpo = corpo.trim_by_plane((-1, 0, 0), -X_CAUDA)      # cabeça + peça intermediária

    h_cab = float(altura(10.0))

    # ---- núcleo: domo em forma de ovo saindo da face de corte
    nucleo = acima(ovo_elipsoide(11.2, 6.5, 3.75, 3.0, h_cab, 0.10), 1.0)

    # ---- acrossomo: capuz em crescente na frente do núcleo, que afina até
    #      sumir nas laterais (recortado pelo contorno do núcleo)
    contorno_nucleo = ovo_elipsoide(11.2, 6.4, 3.65, 40.0, h_cab, 0.10)
    acro = acima(ovo_elipsoide(8.6, 7.2, 4.15, 2.3, h_cab, 0.10), 1.0) - contorno_nucleo

    # ---- mitocôndrias: bainha helicoidal (2 fileiras de 5, cortadas ao meio)
    h_mid = float(altura(28.0))
    mitos = []
    for x in np.linspace(23.6, 33.6, 5):
        for lado in (-1, 1):
            mitos.append(elipsoide(1.18, 1.02, 1.0, (x, lado * 1.47, h_mid), n=40))
    mitocondrias = acima(uniao(mitos), 1.0)

    # ---- axonema (9+2 microtúbulos) em relevo do colo até a ponta da cauda,
    #      com o centríolo (peça de conexão) no colo
    xs_ax = np.concatenate([np.arange(18.9, X_FIM - 0.3, 0.5), [X_FIM - 0.35]])
    pts, raios = [], []
    for x in xs_ax:
        if x < 20.6:
            r = 0.62                       # centríolo
        elif x < 37.0:
            r = 0.48                       # dentro da peça intermediária
        else:
            r = 0.56 - 0.32 * (x - 37.0) / (X_FIM - 37.0)
        pts.append((x, float(y_c(x)), float(altura(x)) + 0.05))
        raios.append(r)
    axonema = acima(corrente(pts, raios, n=16), 0.5)

    # ---- base com títulos
    perfil_base = retangulo_arredondado(-5.0, 85.0, -22.5, 14.5, 5.0)
    topo_base = retangulo_arredondado(-4.4, 84.4, -21.9, 13.9, 4.4)
    base = Manifold.hull_points(np.vstack([
        np.c_[perfil_base, np.full(len(perfil_base), -PLACA)],
        np.c_[topo_base, np.zeros(len(topo_base))]]))

    ALTO_TXT = 0.8
    textos = [
        texto("ESPERMATOZOIDE", 3.6, 58.0, 8.4, -0.1, ALTO_TXT + 0.1),
        texto("CABEÇA", 3.0, 9.5, -14.0, -0.1, ALTO_TXT + 0.1),
        texto("CAUDA", 3.0, 58.5, -14.0, -0.1, ALTO_TXT + 0.1),
        texto("PEÇA INTERMEDIÁRIA", 2.7, 28.6, -19.6, -0.1, ALTO_TXT + 0.1),
    ]
    # chaves (colchetes) indicando cada região, como na ilustração
    t = 0.7
    chaves = []
    for x0, x1 in [(0.0, 19.2), (19.8, 37.0), (37.6, 80.0)]:
        chaves.append(caixa(x0, x1, -9.6 - t / 2, -9.6 + t / 2, -0.1, ALTO_TXT))
        for xx in (x0, x1 - t):
            chaves.append(caixa(xx, xx + t, -9.6 - t / 2, -7.9, -0.1, ALTO_TXT))
    xm = 28.4
    chaves.append(caixa(xm - t / 2, xm + t / 2, -16.4, -9.6, -0.1, ALTO_TXT))
    textos_base = uniao(textos + chaves)

    # tudo apoiado na base (z = 0 é o topo da base)
    partes = [
        ("axonema", "#f3f1ea", axonema),
        ("nucleo", "#7b3fa0", nucleo),
        ("acrossomo", "#3f74c9", acro),
        ("mitocondrias", "#e8702a", mitocondrias),
        ("membrana", "#a9d3f0", corpo),
        ("cauda", "#4a7fd4", cauda),
        ("textos", "#22314f", textos_base),
        ("base", "#e9edf2", base),
    ]
    # ordem de apresentação: base primeiro
    ordem = ["base", "textos", "membrana", "acrossomo", "nucleo",
             "mitocondrias", "axonema", "cauda"]
    return partes, ordem


# --------------------------------------------------------------------- óvulo
#
# Esfera cortada ao meio: a face de cima (z = 0) é o corte, com núcleo,
# mitocôndrias, complexo de Golgi, grânulos corticais etc. em relevo.
# A parte de baixo é uma calota com fundo plano (balanço máx. ~30°).

def ovulo():
    rng = np.random.default_rng(7)
    Z_FUNDO = -17.0
    R_CITO = 26.6     # oolema (membrana do óvulo)
    R_PERI = 27.5     # espaço perivitelino
    R_ZONA = 31.4     # zona pelúcida (externa)
    R_COROA = 35.0    # núcleo sólido da coroa radiada

    def corta(m):
        return acima(abaixo(m, 0.0), Z_FUNDO)

    # ---- citoplasma (ooplasma) e espaço perivitelino (sulco)
    # (as camadas se sobrepõem 0,3 mm; a prioridade das partes define a fronteira)
    citoplasma = corta(esfera(R_PERI + 0.3, n=200))
    sulco = (caixa(-40, 40, -40, 40, -1.3, 5)
             ^ (Manifold.cylinder(10, R_PERI + 0.4, R_PERI + 0.4, 220).translate((0, 0, -5))
                - Manifold.cylinder(10, R_CITO, R_CITO, 220).translate((0, 0, -5))))
    citoplasma = citoplasma - sulco

    # ---- zona pelúcida: casca + anel em relevo no corte, com estrias radiais
    zona = corta(esfera(R_ZONA, n=200)) - esfera(R_PERI, n=200)
    anel = (Manifold.cylinder(2.0, R_ZONA, R_ZONA, 220)
            - Manifold.cylinder(2.0, R_PERI, R_PERI, 220).translate((0, 0, -0.1))
            .scale((1, 1, 1.2)))
    estrias = []
    for k in range(72):
        a = 360 * k / 72
        estrias.append(caixa(R_PERI + 0.8, R_ZONA - 0.6, -0.22, 0.22, 1.55, 3.0)
                       .rotate((0, 0, a)))
    zona = zona + (anel - uniao(estrias))

    # ---- coroa radiada: células em volta de tudo, cada uma com seu núcleo
    centros, raios = [], []
    # anel externo e anel interno na face de corte
    for k in range(40):
        a = 2 * math.pi * k / 40
        centros.append((36.6 * math.cos(a), 36.6 * math.sin(a), 0.2)); raios.append(3.05)
        a2 = a + math.pi / 40
        centros.append((33.6 * math.cos(a2), 33.6 * math.sin(a2), -0.6)); raios.append(2.5)
    # anéis descendo pela calota
    R_CEL = 35.6
    lat = 0.0
    k_anel = 0
    while True:
        lat += 5.6 / R_CEL
        z = -R_CEL * math.sin(lat)
        if z < Z_FUNDO - 1.5:
            break
        rr = R_CEL * math.cos(lat)
        n = int(round(2 * math.pi * rr / 5.7))
        off = 0.5 if k_anel % 2 == 0 else 0.0
        for k in range(n):
            a = 2 * math.pi * (k + off) / n + 0.13 * k_anel
            centros.append((rr * math.cos(a), rr * math.sin(a), z)); raios.append(3.0)
        k_anel += 1
    celulas = [gota(r, c, n=28) for c, r in zip(centros, raios)]
    coroa = corta(esfera(R_COROA, n=200)) + acima(uniao(celulas), Z_FUNDO)
    coroa = coroa - esfera(R_ZONA - 0.3, n=200)    # é uma casca: não invade o sulco
    coroa = acima(coroa, Z_FUNDO)
    # núcleos das células: bolinha escura na superfície externa de cada célula
    nucs = []
    for (cx, cy, cz), r in zip(centros, raios):
        v = np.array([cx, cy, cz])
        if cz > -1.0:      # células do corte: núcleo no topo, levemente para fora
            d = np.array([cx, cy, 0.0]) / math.hypot(cx, cy)
            p = v + r * (0.25 * d + np.array([0, 0, 0.94]))
        else:
            d = v / np.linalg.norm(v)
            p = v + r * d * 0.93
        if p[2] < Z_FUNDO + 1.2:
            continue
        nucs.append(esfera(1.05, p, n=18))
    nucleos_coroa = acima(uniao(nucs), Z_FUNDO)

    # ---- núcleo do óvulo (cortado no topo, mostrando o nucléolo)
    NX, NY = 0.6, 2.0
    nucleo = acima(esfera(9.3, (NX, NY, -4.5), n=160), -12.0)
    nucleo = abaixo(nucleo, 3.6)
    poros = []
    for k in range(46):
        # poros nucleares: pequenas covinhas na superfície do domo
        u, v = rng.uniform(0, 2 * math.pi), rng.uniform(0.55, 1.05)
        p = np.array([NX + 9.3 * math.sin(v) * math.cos(u),
                      NY + 9.3 * math.sin(v) * math.sin(u),
                      -4.5 + 9.3 * math.cos(v)])
        if 0.4 < p[2] < 3.2:
            poros.append(esfera(0.42, p, n=12))
    nucleo = nucleo - uniao(poros)
    nucleolo = acima(esfera(3.0, (NX, NY, 2.9), n=64), 0.0)

    # ---- mitocôndrias: cápsulas cortadas mostrando as cristas
    mitos, cristas = [], []
    L, W = 5.5, 2.8                 # semi-eixos da mitocôndria
    PL, PW = 4.2, 1.75              # semi-eixos da cavidade (matriz)
    for (mx, my) in [(-9.2, 14.3), (10.1, 15.1), (-12.2, -13.6), (4.1, -15.6)]:
        ang = math.degrees(math.atan2(my, mx)) + 90   # orientação tangencial
        cavidade = elipsoide(PL, PW, 10, n=72)
        casca = abaixo(elipsoide(L, W, 6.0, n=72), 2.3)
        casca = acima(casca - (caixa(-6, 6, -4, 4, 1.21, 4) ^ cavidade), -2.0)
        cr = []
        comp = 2 * PW * 0.70
        for i, x in enumerate(np.linspace(-3.0, 3.0, 5)):
            lado = 1 if i % 2 == 0 else -1
            cr.append(elipsoide(0.35, comp / 2, 0.8, (x, lado * (PW - comp / 2), 1.2)))
        cr = acima(uniao(cr) ^ elipsoide(PL + 0.25, PW + 0.25, 10, n=72), 1.0)  # encaixa na parede
        mitos.append(casca.rotate((0, 0, ang)).translate((mx, my, 0)))
        cristas.append(cr.rotate((0, 0, ang)).translate((mx, my, 0)))
    mitocondrias = uniao(mitos)
    mito_cristas = uniao(cristas)

    # ---- complexo de Golgi: 5 cisternas curvas empilhadas + vesículas
    GX, GY = -15.0, -0.6
    golgi = []
    C = np.array([GX + 9.6, GY])
    for i, R in enumerate([6.4, 8.0, 9.6, 11.2, 12.8]):
        span = (5.0 + 0.2 * i) / R
        angs = np.linspace(math.pi - span, math.pi + span, 22)
        pts = [(C[0] + R * math.cos(a), C[1] + R * math.sin(a), 0.0) for a in angs]
        rs = [0.5 + 0.08 * (abs(j - 10.5) / 10.5) ** 4 for j in range(len(angs))]
        golgi.append(corrente(pts, rs, n=16, escala_z=2.6))
        for s in (-1, 1):  # vesículas brotando das pontas
            a = math.pi + s * (span + 0.75 / R)
            golgi.append(esfera(0.52, (C[0] + (R + 0.25) * math.cos(a),
                                       C[1] + (R + 0.25) * math.sin(a), 0.0), n=16))
    golgi = acima(uniao(golgi), -1.5)

    # ---- vesícula (lisossomo) com conteúdo escuro
    VX, VY = 14.6, -5.9
    vesicula = acima(esfera(2.5, (VX, VY, -0.6), n=48), -1.0)
    vesicula = vesicula - esfera(1.6, (VX, VY, 1.9), n=32)
    conteudo = acima(esfera(1.25, (VX, VY, 0.4), n=32), -0.5)

    # ---- grânulos corticais (anel logo abaixo da membrana)
    graos = []
    for k in range(52):
        a = 2 * math.pi * k / 52 + rng.uniform(-0.03, 0.03)
        r = 24.9 + rng.uniform(-0.35, 0.35)
        graos.append(esfera(0.85, (r * math.cos(a), r * math.sin(a), -0.15), n=16))
    obstaculos = [((NX, NY), 10.6), ((VX, VY), 3.8), ((GX, GY), 8.5),
                  ((-9.2, 14.3), 6.8), ((10.1, 15.1), 6.8),
                  ((-12.2, -13.6), 6.8), ((4.1, -15.6), 6.8)]
    pontos = []
    tentativas = 0
    while len(pontos) < 45 and tentativas < 5000:
        tentativas += 1
        r = 23.2 * math.sqrt(rng.uniform(0, 1))
        a = rng.uniform(0, 2 * math.pi)
        p = (r * math.cos(a), r * math.sin(a))
        if any(math.dist(p, c) < rr for c, rr in obstaculos):
            continue
        if any(math.dist(p, q) < 3.0 for q in pontos):
            continue
        pontos.append(p)
    granulos = acima(uniao(graos + [conteudo]), -1.0)
    # pequenas vesículas espalhadas pelo citoplasma (pontinhos claros)
    vesiculas = [vesicula] + [esfera(rng.uniform(0.5, 0.65), (x, y, -0.2), n=14)
                              for (x, y) in pontos]
    vesicula = acima(uniao(vesiculas), -1.0)

    partes = [
        ("nucleolo", "#3a2470", nucleolo),
        ("nucleos_coroa", "#5e2a74", nucleos_coroa),
        ("mitocondrias_cristas", "#f7c24a", mito_cristas),
        ("granulos", "#b3263a", granulos),
        ("vesiculas", "#f6eeea", vesicula),
        ("mitocondrias", "#e8702a", mitocondrias),
        ("golgi", "#3f6fd6", golgi),
        ("nucleo", "#8a63d2", nucleo),
        ("zona_pelucida", "#f2913f", zona),
        ("citoplasma", "#f6b4a6", citoplasma),
        ("coroa_radiada", "#c98ccf", coroa),
    ]
    ordem = ["coroa_radiada", "nucleos_coroa", "zona_pelucida", "citoplasma",
             "nucleo", "nucleolo", "mitocondrias", "mitocondrias_cristas",
             "golgi", "granulos", "vesiculas"]
    # apoia o fundo plano em z = 0
    return [(n, c, m.translate((0, 0, -Z_FUNDO))) for n, c, m in partes], ordem


# --------------------------------------------------------------------- saída

def para_trimesh(m):
    mesh = m.to_mesh()
    return trimesh.Trimesh(vertices=np.asarray(mesh.vert_properties)[:, :3],
                           faces=np.asarray(mesh.tri_verts), process=False)


def limpar(m, vol_min=0.05):
    """Descarta lascas degeneradas (volume ~0) que sobram das operações booleanas."""
    pedacos = [p for p in m.decompose() if p.volume() > vol_min]
    return uniao(pedacos)


def salvar(nome_modelo, modelo, escala, pasta):
    """modelo = (partes em ordem de prioridade, ordem de apresentação)."""
    originais, ordem = modelo
    # peça única: união das partes originais (que se sobrepõem de verdade)
    completo = limpar(uniao([m for _, _, m in originais])).scale((escala,) * 3)
    # peças por cor: sem sobreposição, encaixando face a face
    d = {n: (n, c, m) for n, c, m in separar_por_prioridade(originais)}
    partes = [d[n] for n in ordem]
    sub = os.path.join(pasta, nome_modelo)
    os.makedirs(sub, exist_ok=True)
    for f in os.listdir(sub):
        if f.endswith(".stl"):
            os.remove(os.path.join(sub, f))
    info = []
    for i, (nome, cor, m) in enumerate(partes, 1):
        if m.is_empty():
            print(f"  ! parte vazia: {nome}")
            continue
        m = limpar(m).scale((escala, escala, escala))
        arq = f"{i:02d}_{nome}.stl"
        v = np.asarray(m.to_mesh().vert_properties)[:, :3]
        dup = len(v) - len(np.unique(v, axis=0))
        if dup:
            print(f"  ! {nome}: {dup} vértices coincidentes")
        para_trimesh(m).export(os.path.join(sub, arq))
        info.append({"arquivo": f"{nome_modelo}/{arq}", "parte": nome, "cor": cor,
                     "volume_cm3": round(m.volume() / 1000, 2)})
        print(f"  {arq:32s} {cor}  {m.volume() / 1000:7.2f} cm³  {m.num_tri():7d} tri")
    para_trimesh(completo).export(os.path.join(pasta, f"{nome_modelo}_completo.stl"))
    x0, y0, z0, x1, y1, z1 = completo.bounding_box()
    dims = (round(x1 - x0, 1), round(y1 - y0, 1), round(z1 - z0, 1))
    print(f"  -> {nome_modelo}_completo.stl  {dims[0]} x {dims[1]} x {dims[2]} mm, "
          f"{completo.volume() / 1000:.1f} cm³, {len(completo.decompose())} corpo(s), "
          f"gênero {completo.genus()}")
    return {"modelo": nome_modelo, "dimensoes_mm": dims, "partes": info}


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--escala", type=float, default=1.0,
                    help="1.0 = 80 mm (padrão); 1.25 = 100 mm; 0.75 = 60 mm")
    ap.add_argument("--saida", default=os.path.join(AQUI, "stl"))
    args = ap.parse_args()
    os.makedirs(args.saida, exist_ok=True)

    resumo = []
    print("Espermatozoide...")
    resumo.append(salvar("espermatozoide", espermatozoide(), args.escala, args.saida))
    print("Óvulo...")
    resumo.append(salvar("ovulo", ovulo(), args.escala, args.saida))
    with open(os.path.join(args.saida, "partes.json"), "w", encoding="utf-8") as f:
        json.dump(resumo, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
