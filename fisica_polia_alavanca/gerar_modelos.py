#!/usr/bin/env python3
"""
Máquinas simples para imprimir em 3D (projeto de Física):

  POLIA    - pórtico com uma polia fixa e uma polia móvel de verdade
             (barbante + moedas nos baldinhos mostram que F = P/2)
  ALAVANCA - gangorra de moedas com posições numeradas
             (mostra que F1 x d1 = F2 x d2)

Todas as peças saem já deitadas na posição de impressão, sem suporte.

Saída (pasta stl/):
  polia/NN_<peça>.stl      peças soltas (quantidades no README e em pecas.json)
  polia/polia_mesa_completa.stl  tudo arrumado numa mesa só (cabe em 180 x 180 mm)
  alavanca/...             idem

Uso:  python3 gerar_modelos.py              gera os STL
      python3 gerar_modelos.py --verificar  só confere folgas e encaixes

Requer: pip install manifold3d trimesh numpy matplotlib
"""
import argparse
import json
import math
import os

import numpy as np
from manifold3d import CrossSection, FillRule, JoinType, Manifold, OpType
import trimesh

AQUI = os.path.dirname(os.path.abspath(__file__))
FONTE = "/usr/share/fonts/opentype/inter/Inter-Bold.otf"

# ============================================================== parâmetros (mm)

# ---- pórtico da polia (impresso deitado; a espessura é a altura de impressão)
T_F = 7.0            # espessura do pórtico
W_OUT = 150.0        # largura externa
LEG_W = 14.0         # largura de cada perna
BAR_H = 6.0          # altura da travessa de cima (onde os moitões penduram)
H_PADRAO = 168.0     # altura total: cabe em mesa de 180 mm
H_ALTO = 210.0       # versão alta, para mesa de 220 mm ou mais (mais curso)
X_PE = W_OUT / 2 - LEG_W / 2
PE_T, PE_H, PE_L = 6.0, 18.0, 104.0      # pés (espessura, altura, comprimento)
TRAV_T, Y_TRAV = 4.0, -38.0              # placa do título, na frente dos pés
ALTO_RELEVO = 0.6    # altura das letras e marcas em relevo

# ---- roldana
R_ROL, T_ROL = 15.0, 7.0
SULCO = 2.3                               # profundidade do sulco em V
R_CORDA = R_ROL - SULCO + 0.76            # raio em que o barbante trabalha
X_FIXA = 25.0                             # posição da polia fixa na travessa
X_FIXA2 = -27.0                           # 2º rebaixo: as duas polias como fixas
X_MOVEL = X_FIXA - 2 * R_CORDA            # com isso os trechos de corda ficam
X_ANC = X_FIXA - 3 * R_CORDA              # verticais (polia móvel ideal)

# ---- moitão (garfo que segura a roldana, com gancho)
GA = T_ROL / 2 + 0.6     # meia-distância entre os braços
GW = T_F / 2 + 0.3       # meia-largura da janela do gancho (abraça a travessa)
TA = 2.2                 # espessura dos braços (flexionam para a roldana entrar)
V_PONTA = -6.5
V_P0 = R_ROL + 6.0       # ponte: deixa 6 mm para passar o barbante
V_P1 = V_P0 + 3.0
V_C = V_P1 + 10.5        # teto da janela do gancho
V_TOPO = V_C + 2.8
LABIO = 3.5
L_MOI = 10.0             # largura do moitão (altura de impressão)
TELHADO = 0.8            # o teto em "V" centraliza a alça do balde
PINO_R0, PINO_R1, PINO_H = 2.2, 0.8, 1.3   # pivô cônico que entra na roldana
FOLGA_AXIAL = 0.3

# ---- baldinho de moedas
B_RO, B_RI = 16.3, 14.3          # cabe moeda de até 27 mm (R$ 1)
B_FUNDO, B_PROF = 1.8, 13.4
B_H = B_FUNDO + B_PROF
B_NERV = 17.5                    # nervura externa que sustenta a alça
ALCA_L = 4.0                     # largura da alça
ALCA_POSTE = 4.0
ALCA_VAO = 7.0                   # meia-largura do trecho reto da alça
ALCA_E = 2.4
ALCA_Z = B_H + ALCA_POSTE + (B_RI - ALCA_VAO)   # face de baixo da alça

# ---- alavanca (gangorra de moedas)
PASSO = 8.0                      # distância entre posições numeradas
POSICOES = range(3, 11)          # posições 3..10 (24 a 80 mm do apoio)
BAR_L, BAR_W, BAR_A = 170.0, 24.0, 8.0
FENDA_L, FENDA_PROF, FENDA_R = 3.4, 5.4, 30.0
# As fendas são inclinadas 10° para fora: a moeda sempre deita na parede de
# fora, então o centro dela cai na posição certa seja qual for a espessura.
FENDA_INC = 10.0
FENDA_DX = ((FENDA_L - 2.3) / 2 * math.cos(math.radians(FENDA_INC))
            + 12.0 * math.sin(math.radians(FENDA_INC)))   # moeda média: 24 x 2,3 mm
Z_APICE = 24.0                   # altura do pivô acima do fundo da barra
ORELHA_Y0, ORELHA_Y1 = 14.5, 18.0
ORELHA_VAO = 13.0
ABA_Y, ABA_H = 19.0, 3.0         # abas com os números
Z_PIVO = 41.0                    # altura do fio da "faca" acima da mesa
LAMINA_Y, LAMINA_T = 16.25, 5.0
BASE_X, BASE_Y0, BASE_Y1, BASE_T = 45.0, -46.0, 24.0, 3.0

DENSIDADE_PLA = 1.24             # g/cm³


# ================================================================== utilitários

def ret(x0, x1, y0, y1):
    return CrossSection.square((x1 - x0, y1 - y0)).translate((x0, y0))


def circ(r, c=(0.0, 0.0), n=72):
    return CrossSection.circle(r, n).translate(c)


def poli(pts):
    return CrossSection([np.asarray(pts, dtype=float)], FillRule.NonZero)


def fechar(cs, r):
    """Arredonda os cantos internos (filetes de raio r)."""
    return cs.offset(r, JoinType.Round, 2.0, 64).offset(-r, JoinType.Round, 2.0, 64)


def abrir(cs, r):
    """Arredonda os cantos externos (raio r)."""
    return cs.offset(-r, JoinType.Round, 2.0, 64).offset(r, JoinType.Round, 2.0, 64)


def fenda(c, larg, prof, borda, sentido):
    """Fenda de encaixe em meia-madeira, levemente afunilada: entra fácil
    (0,3 mm de folga por lado na boca) e firma no fundo (0,05 mm por lado).
    c = centro, borda = onde fica a boca, sentido = +1 sobe / -1 desce."""
    wm, wr = larg + 0.6, larg + 0.1
    b0, b1 = borda - sentido * 1.0, borda + sentido * prof
    return poli([(c - wm / 2, b0), (c + wm / 2, b0), (c + wm / 2, borda),
                 (c + wr / 2, b1), (c - wr / 2, b1), (c - wm / 2, borda)])


def uniao(lista):
    lista = [m for m in lista if not m.is_empty()]
    return Manifold.batch_boolean(lista, OpType.Add) if lista else Manifold()


def T(m, linhas):
    """Transformação afim 3x4 (as três primeiras colunas são uma rotação)."""
    return m.transform(np.array(linhas, dtype=float))


def caixa(x0, x1, y0, y1, z0, z1):
    return Manifold.cube((x1 - x0, y1 - y0, z1 - z0)).translate((x0, y0, z0))


def tronco_u(u0, u1, r0, r1, v, w, n=48):
    """Tronco de cone ao longo do eixo u: raio r0 em u0, raio r1 em u1."""
    s = 1.0 if u1 > u0 else -1.0
    c = Manifold.cylinder(abs(u1 - u0), r0, r1, n)
    return T(c, [[0, 0, s, u0], [0, 1, 0, v], [-s, 0, 0, w]])


def _fonte():
    from matplotlib.font_manager import FontProperties
    if os.path.exists(FONTE):
        return FontProperties(fname=FONTE)
    return FontProperties(family="DejaVu Sans", weight="bold")


def texto(s, cap, x=0.0, y=0.0, alinhar="center", rot=0.0):
    """Texto como contorno 2D; cap = altura das maiúsculas; y = linha de base."""
    from matplotlib.textpath import TextPath
    fp = _fonte()
    ref = TextPath((0, 0), "H", size=100, prop=fp).get_extents()
    tp = TextPath((0, 0), s, size=100 * cap / ref.height, prop=fp)
    polys = [np.asarray(p) for p in tp.to_polygons(closed_only=True) if len(p) >= 3]
    ext = tp.get_extents()
    dx = {"center": -(ext.x0 + ext.x1) / 2, "left": -ext.x0, "right": -ext.x1}[alinhar]
    cs = CrossSection(polys, FillRule.EvenOdd).translate((dx, 0))
    if rot:
        cs = cs.rotate(rot)
    return cs.translate((x, y))


def largura_texto(s, cap):
    b = texto(s, cap, alinhar="left").bounds()
    return b[2] - b[0]


def relevo(cs, z_topo):
    """Extrusão das marcas em relevo, começando 0,2 mm dentro da peça."""
    return cs.extrude(ALTO_RELEVO + 0.2).translate((0, 0, z_topo - 0.2))


# ================================================================ POLIA: peças
#
# Cada função devolve a peça NA POSIÇÃO DE IMPRESSÃO (apoiada em z = 0).

def com_relevo(corpo, marcas, separado):
    """Junta o relevo ao corpo (ou devolve os dois, para colorir nas imagens)."""
    return (corpo, marcas) if separado else corpo + marcas


def portico(h_total=H_PADRAO, separado=False):
    """Pórtico: duas pernas + travessa fina onde os moitões se penduram.
    Perfil desenhado em (x, z) e extrudado na espessura."""
    H = h_total - BAR_H
    xl0, xl1 = W_OUT / 2 - LEG_W, W_OUT / 2
    cs = ret(-xl1, -xl0, 0, h_total) + ret(xl0, xl1, 0, h_total) + ret(-xl1, xl1, H, h_total)
    cs = abrir(fechar(cs, 8.0), 2.0)
    # olhal para amarrar a ponta fixa do barbante
    cs = fechar(cs + circ(4.6, (X_ANC, H - 2.4)), 1.0)
    cs = cs - circ(2.3, (X_ANC, H - 2.9))
    # rebaixos que posicionam os moitões pendurados na travessa
    for xf in (X_FIXA, X_FIXA2):
        cs = cs - ret(xf - L_MOI / 2 - 0.4, xf + L_MOI / 2 + 0.4, h_total - 1.0, h_total + 1)
    # encaixes dos pés (meia-madeira)
    for s in (-1, 1):
        cs = cs - fenda(s * X_PE, PE_T, PE_H / 2 + 0.3, 0.0, +1)
    corpo = cs.extrude(T_F)

    # réguas em centímetros nas duas pernas (altura acima da mesa)
    marcas = []
    z = 20
    while z <= H - 14:
        comp = 4.5 if z % 10 == 0 else 2.5
        for s in (-1, 1):
            x0, x1 = sorted((s * (xl0 + 0.7), s * (xl0 + 0.7 + comp)))
            marcas.append(ret(x0, x1, z - 0.4, z + 0.4))
            if z % 20 == 0:
                marcas.append(texto(str(z // 10), 3.8, s * (xl0 + 9.6), z - 1.9))
        z += 5
    for s in (-1, 1):
        marcas.append(texto("cm", 3.0, s * (xl0 + 7.0), H - 7.5))
    return com_relevo(corpo, relevo(uniao_cs(marcas), T_F), separado)


def uniao_cs(lista):
    return CrossSection.batch_boolean(lista, OpType.Add)


def pe():
    """Pé: perfil em (y, z), encaixa por baixo na perna e segura a placa do título."""
    cs = (ret(-PE_L / 2, PE_L / 2, 0, PE_H - 6) + circ(6, (-PE_L / 2 + 6, PE_H - 6))
          + circ(6, (PE_L / 2 - 6, PE_H - 6))).hull()
    cs = cs - fenda(0.0, T_F, PE_H / 2, PE_H, -1)
    cs = cs - fenda(Y_TRAV, TRAV_T, PE_H / 2, PE_H, -1)
    cs = cs - ret(-PE_L / 2 + 12, PE_L / 2 - 12, -1, 0.8)     # apoia só nas pontas
    return cs.extrude(PE_T)


def placa_titulo(titulo="POLIA FIXA E POLIA MÓVEL", separado=False):
    """Placa do título: une os dois pés pela frente. Perfil em (x, z)."""
    cs = abrir(ret(-W_OUT / 2, W_OUT / 2, 0, PE_H), 3.0)
    for s in (-1, 1):
        cs = cs - fenda(s * X_PE, PE_T, PE_H / 2 + 0.3, 0.0, +1)
    cap = 6.6
    util = 2 * (X_PE - PE_T / 2 - 2.5)
    cap = min(cap, cap * util / largura_texto(titulo, cap))
    return com_relevo(cs.extrude(TRAV_T),
                      relevo(texto(titulo, cap, 0, (PE_H - cap) / 2), TRAV_T), separado)


def perfil_moitao():
    yo = GA + TA
    baixo = (ret(GA, yo, V_PONTA, V_P1) + ret(-yo, -GA, V_PONTA, V_P1)
             + ret(-yo, yo, V_P0, V_P1))
    baixo = abrir(fechar(baixo, 1.2), 0.7)
    gancho = (ret(GW, yo, V_P0, V_TOPO) + ret(-yo, yo, V_C, V_TOPO)
              + ret(-yo, -GW, V_C - LABIO, V_TOPO))
    gancho = abrir(gancho, 0.7)
    cs = baixo + gancho
    # teto da janela em "telhado": a alça do balde escorrega para o centro
    cs = cs - poli([(-GW, V_C - 0.01), (GW, V_C - 0.01), (0, V_C + TELHADO)])
    # rampas de entrada nas pontas dos braços
    for s in (-1, 1):
        cs = cs - poli([(s * (GA - 0.01), V_PONTA + 3.5), (s * (GA - 0.01), V_PONTA - 1),
                        (s * (GA + 1.2), V_PONTA - 1), (s * (GA + 1.2), V_PONTA)])
    return cs


def moitao():
    """Moitão: garfo flexível com dois pivôs cônicos + gancho. Perfil em
    (y, z) do conjunto, extrudado na largura. A roldana entra por pressão."""
    m = perfil_moitao().extrude(L_MOI)
    for s in (-1, 1):
        face = s * (GA - FOLGA_AXIAL)          # ressalto de encosto
        m += tronco_u(s * (GA + 0.3), face, 3.2, 3.2, 0.0, L_MOI / 2)
        m += tronco_u(face + s * 0.01, face - s * PINO_H, PINO_R0, PINO_R1, 0.0, L_MOI / 2)
        fora = s * (GA + TA)                   # "cabeça do eixo" por fora (enfeite)
        m += tronco_u(fora - s * 0.3, fora + s * 0.8, 3.5, 2.7, 0.0, L_MOI / 2)
    return m


def roldana():
    """Roldana com sulco em V, 4 furos e rebaixos cônicos para os pivôs."""
    R, Tw = R_ROL, T_ROL
    rb = 1.1                                   # furo central
    # boca do rebaixo: o cone do pivô com 0,35 mm de folga radial
    rc = PINO_R0 - FOLGA_AXIAL * (PINO_R0 - PINO_R1) / PINO_H + 0.35
    dc = (rc - rb) * PINO_H / (PINO_R0 - PINO_R1)
    g0 = Tw / 2 - 0.3 - SULCO                  # borda do sulco
    perfil = poli([(rb, dc), (rc, 0), (R - 0.4, 0), (R, 0.4), (R, g0),
                   (R - SULCO, Tw / 2 - 0.3), (R - SULCO, Tw / 2 + 0.3),
                   (R, Tw - g0), (R, Tw - 0.4), (R - 0.4, Tw), (rc, Tw), (rb, Tw - dc)])
    m = perfil.revolve(128)
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        m -= Manifold.cylinder(Tw + 2, 3.25, 3.25, 48).translate(
            (7.6 * math.cos(a), 7.6 * math.sin(a), -1))
    return m


def balde():
    """Baldinho para moedas, com alça em arco que imprime sem suporte."""
    copo = poli([(0, 0), (B_RO - 0.6, 0), (B_RO, 0.6), (B_RO, B_H), (B_RI, B_H),
                 (B_RI, B_FUNDO), (0, B_FUNDO)]).revolve(128)
    zp = B_H + ALCA_POSTE
    z_topo = ALCA_Z + ALCA_E
    x_topo = B_NERV - (z_topo - zp)            # as hastes sobem a 45°
    alca = poli([(B_RI, B_H - 0.3), (B_NERV, B_H - 0.3), (B_NERV, zp), (x_topo, z_topo),
                 (-x_topo, z_topo), (-B_NERV, zp), (-B_NERV, B_H - 0.3), (-B_RI, B_H - 0.3),
                 (-B_RI, zp), (-ALCA_VAO, ALCA_Z), (ALCA_VAO, ALCA_Z), (B_RI, zp)])
    alca = T(alca.extrude(ALCA_L), [[1, 0, 0, 0], [0, 0, -1, ALCA_L / 2], [0, 1, 0, 0]])
    # nervuras externas: sustentam os postes da alça desde a mesa de impressão
    nerv = uniao([caixa(min(s * (B_RO - 0.4), s * B_NERV), max(s * (B_RO - 0.4), s * B_NERV),
                        -ALCA_L / 2, ALCA_L / 2, 0, B_H + 0.5) for s in (-1, 1)])
    return copo + alca + nerv


# ============================================================= POLIA: montagem

def z_eixo_fixa(h_total):
    """Altura do eixo da polia fixa com o moitão pendurado no rebaixo."""
    return (h_total - 1.0) - (V_C + TELHADO * (1 - (T_F / 2) / GW))


def z_fundo_balde(z_eixo):
    """Fundo do balde pendurado no gancho de um moitão virado para baixo."""
    return z_eixo - (V_C + TELHADO * (1 - (ALCA_L / 2) / GW)) - ALCA_Z


def curso_polia(h_total):
    z_fixa = z_eixo_fixa(h_total)
    z_min = -z_fundo_balde(0.0)
    z_max = z_fixa - 2 * R_ROL - 1.5
    return z_min, z_max


def montagem_polia(h_total=H_PADRAO, z_movel=None, z_balde_forca=None):
    """Peças da polia na posição montada: [(nome, cor, sólido)].
    Mundo: x = largura, z = altura, y = profundidade (frente = y negativo)."""
    z_min, z_max = curso_polia(h_total)
    if z_movel is None:
        z_movel = (z_min + z_max) / 2
    z_fixa = z_eixo_fixa(h_total)
    if z_balde_forca is None:
        z_balde_forca = z_fundo_balde(z_movel) + 30
    rold = T(roldana(), [[1, 0, 0, 0], [0, 0, -1, T_ROL / 2], [0, 1, 0, 0]])
    moi = moitao()
    cinza, azul, verm, amar = "#e8eaee", "#2f5fb3", "#d8402f", "#f2b632"
    pecas = [
        ("portico", cinza, T(portico(h_total), [[1, 0, 0, 0], [0, 0, -1, T_F / 2], [0, 1, 0, 0]])),
        ("placa_titulo", cinza, T(placa_titulo(), [[1, 0, 0, 0], [0, 0, -1, Y_TRAV + TRAV_T / 2],
                                                   [0, 1, 0, 0]])),
    ]
    for i, s in enumerate((-1, 1)):
        pecas.append((f"pe_{i + 1}", cinza,
                      T(pe(), [[0, 0, 1, s * X_PE - PE_T / 2], [1, 0, 0, 0], [0, 1, 0, 0]])))
    pecas += [
        ("moitao_fixo", azul, T(moi, [[0, 0, 1, X_FIXA - L_MOI / 2], [1, 0, 0, 0], [0, 1, 0, z_fixa]])),
        ("roldana_fixa", verm, rold.translate((X_FIXA, 0, z_fixa))),
        ("moitao_movel", azul, T(moi, [[0, 0, -1, X_MOVEL + L_MOI / 2], [1, 0, 0, 0],
                                       [0, -1, 0, z_movel]])),
        ("roldana_movel", verm, rold.translate((X_MOVEL, 0, z_movel))),
        ("balde_carga", amar, balde().translate((X_MOVEL, 0, z_fundo_balde(z_movel)))),
        ("balde_forca", amar, balde().translate((X_FIXA + R_CORDA, 0, z_balde_forca))),
    ]
    return pecas


# ============================================================= ALAVANCA: peças

def barra(separado=False):
    """Barra da gangorra: fendas numeradas para moedas em pé e duas "orelhas"
    em A que se apoiam no fio das lâminas da base (pivô acima das moedas:
    equilíbrio estável, como numa balança)."""
    L2, W2 = BAR_L / 2, BAR_W / 2
    m = abrir(ret(-L2, L2, -W2, W2), 2.0).extrude(BAR_A)
    extras = []
    for sx in (-1, 1):
        for sy in (-1, 1):
            # ressaltos que sustentam as orelhas
            x0, x1 = sorted((sx * 12.0, sx * 18.5))
            y0, y1 = sorted((sy * (W2 - 0.5), sy * ORELHA_Y1))
            extras.append(caixa(x0, x1, y0, y1, 0, BAR_A))
            # abas com os números
            x0, x1 = sorted((sx * 20.0, sx * (L2 - 0.5)))
            yi, yr = sy * (W2 - 2.0), sy * (ABA_Y - 2.5)
            aba = (ret(x0, x1, min(yi, yr), max(yi, yr))
                   + circ(2.5, (x0 + 2.5, yr)) + circ(2.5, (x1 - 2.5, yr))).hull()
            extras.append(aba.extrude(ABA_H))
    m += uniao(extras)
    # fendas: fundo côncavo (a moeda rola para o centro) e inclinadas para fora
    disco = T(Manifold.cylinder(FENDA_L, FENDA_R, FENDA_R, 256, True),
              [[0, 0, 1, 0], [0, 1, 0, 0], [-1, 0, 0, FENDA_R]])
    disco ^= caixa(-5, 5, -(W2 + 0.6), W2 + 0.6, 0, FENDA_PROF + 6)
    m -= uniao([disco.rotate((0, s * FENDA_INC, 0))
                .translate((s * (PASSO * k - FENDA_DX), 0, BAR_A - FENDA_PROF))
                for k in POSICOES for s in (-1, 1)])
    # orelhas em "A": o vão em V invertido assenta no fio da lâmina
    a, zc = ORELHA_VAO, BAR_A - 0.3
    perfil = poli([(a, zc), (18.5, zc), (18.5, BAR_A + 2.0), (1.0, Z_APICE + 3.5),
                   (-1.0, Z_APICE + 3.5), (-18.5, BAR_A + 2.0), (-18.5, zc), (-a, zc),
                   (-a, Z_APICE - a), (0, Z_APICE), (a, Z_APICE - a)])
    esp = ORELHA_Y1 - ORELHA_Y0
    orelha = perfil.extrude(esp)
    for sy in (-1, 1):
        y_ext = ORELHA_Y1 if sy > 0 else -ORELHA_Y0
        m += T(orelha, [[1, 0, 0, 0], [0, 0, -1, y_ext], [0, 1, 0, 0]])
    # números: lidos de frente na aba da frente e por trás na aba de trás
    numeros = []
    yc = (W2 + ABA_Y) / 2 + 0.2
    for k in POSICOES:
        for sx in (-1, 1):
            numeros.append(texto(str(k), 4.0, sx * PASSO * k, -yc - 2.0))
            numeros.append(texto(str(k), 4.0, 0, -2.0, rot=180).translate((sx * PASSO * k, yc)))
    return com_relevo(m, relevo(uniao_cs(numeros), ABA_H), separado)


def base_alavanca(titulo="ALAVANCA", formula="F1 × d1 = F2 × d2", separado=False):
    """Base com as duas lâminas (ponto de apoio). Imprime em pé, como está."""
    placa = abrir(ret(-BASE_X, BASE_X, BASE_Y0, BASE_Y1), 6.0).extrude(BASE_T)
    zb = BASE_T - 0.5
    d_reta = 5.0 / math.tan(math.radians(22))
    lam = poli([(0, Z_PIVO), (5, Z_PIVO - d_reta), (5, 12), (14, 3), (14, zb), (-14, zb),
                (-14, 3), (-5, 12), (-5, Z_PIVO - d_reta)]).extrude(LAMINA_T)
    m = placa
    for sy in (-1, 1):
        y_ext = sy * LAMINA_Y + LAMINA_T / 2
        m += T(lam, [[1, 0, 0, 0], [0, 0, -1, y_ext], [0, 1, 0, 0]])
        for lado in (-1, 1):             # reforços na base de cada lâmina
            yf = sy * LAMINA_Y + lado * (LAMINA_T / 2 - 0.3)
            cunha = poli([(0, zb), (lado * 4.3, zb), (0, zb + 6.8)]).extrude(24)
            m += T(cunha, [[0, 0, 1, -12], [1, 0, 0, yf], [0, 1, 0, 0]])
    letras = [texto(titulo, 7.0, 0, -33.5), texto(formula, 4.2, 0, -42.5)]
    return com_relevo(m, relevo(uniao_cs(letras), BASE_T), separado)


def moeda(d=23.0, e=2.85, n=64):
    """Moeda só para visualização/conferência (padrão: 50 centavos)."""
    return Manifold.cylinder(e, d / 2, d / 2, n, True)


def moeda_na_fenda(k, lado, d=23.0, e=2.85, folga=0.0):
    """Moeda encostada na parede de fora da fenda k (coordenadas da barra)."""
    m = T(moeda(d, e), [[0, 0, 1, 0], [0, 1, 0, 0], [-1, 0, 0, 0]])
    m = m.translate((lado * (FENDA_L / 2 - e / 2 - folga), 0, d / 2 + folga))
    return (m.rotate((0, lado * FENDA_INC, 0))
            .translate((lado * (PASSO * k - FENDA_DX), 0, BAR_A - FENDA_PROF)))


def centro_moeda(k, d, e):
    """Distância do centro da moeda ao apoio (mm), para conferir o braço."""
    a = math.radians(FENDA_INC)
    return PASSO * k - FENDA_DX + (FENDA_L - e) / 2 * math.cos(a) + d / 2 * math.sin(a)


def inclinar(m, graus):
    """Gira a barra em torno do fio da lâmina (positivo = lado direito desce)."""
    return m.translate((0, 0, -Z_APICE)).rotate((0, graus, 0)).translate((0, 0, Z_PIVO))


def inclinacao_maxima():
    """Ângulo em que a ponta da barra encosta na mesa."""
    f = lambda t: BAR_L / 2 * math.sin(t) + Z_APICE * math.cos(t) - Z_PIVO
    a, b = 0.0, math.radians(40)
    for _ in range(60):
        c = (a + b) / 2
        a, b = (c, b) if f(c) < 0 else (a, c)
    return math.degrees(a)


def montagem_alavanca(graus=0.0):
    return [("base", "#e8eaee", base_alavanca()),
            ("barra", "#2a9d8f", inclinar(barra(), graus))]


# ====================================================================== mesas

def no_chao(m, x=0.0, y=0.0, rot=0.0):
    """Apoia a peça em z = 0 e centraliza em (x, y), opcionalmente girada."""
    if rot:
        m = m.rotate((0, 0, rot))
    x0, y0, z0, x1, y1, _ = m.bounding_box()
    return m.translate((x - (x0 + x1) / 2, y - (y0 + y1) / 2, -z0))


def itens_mesa_polia(h_total=H_PADRAO):
    """Todas as peças da polia arrumadas: as menores vão dentro do vão do
    pórtico, então a mesa inteira ocupa só 150 x 168 mm."""
    return [no_chao(portico(h_total), 75, h_total / 2),
            no_chao(placa_titulo(), 124, 79, rot=90),
            no_chao(pe(), 26, 54, rot=90), no_chao(pe(), 47, 54, rot=90),
            no_chao(moitao(), 26, 132), no_chao(moitao(), 46, 132),
            no_chao(balde(), 78, 22), no_chao(balde(), 78, 58),
            no_chao(roldana(), 78, 94), no_chao(roldana(), 78, 128)]


def itens_mesa_alavanca():
    return [no_chao(barra(), 85, 19), no_chao(base_alavanca(), 85, 79)]


def mesa_polia(h_total=H_PADRAO):
    return uniao(itens_mesa_polia(h_total))


def mesa_alavanca():
    return uniao(itens_mesa_alavanca())


# ================================================================ verificação

def interferencia(a, b):
    return (a ^ b).volume()


def verificar():
    ok = True

    def confere(nome, valor, limite, maior=False):
        nonlocal ok
        bom = valor >= limite if maior else valor <= limite
        ok &= bom
        print(f"  {'ok ' if bom else 'ERRO'} {nome}: {valor:.3f}"
              f" ({'mín' if maior else 'máx'} {limite})")

    print("Polia: interferências com tudo montado (mm³)")
    for h in (H_PADRAO, H_ALTO):
        z_min, z_max = curso_polia(h)
        print(f"  pórtico de {h:.0f} mm: a carga sobe {z_max - z_min:.0f} mm "
              f"(puxando {2 * (z_max - z_min):.0f} mm de barbante)")
        for zm in (z_min, z_max):
            p = dict((n, m) for n, _, m in montagem_polia(h, zm, 40.0))
            nomes = list(p)
            pior = max(((interferencia(p[a], p[b]), a, b) for i, a in enumerate(nomes)
                        for b in nomes[i + 1:]), key=lambda t: t[0])
            confere(f"h={h:.0f} z={zm:.0f} pior par {pior[1]} x {pior[2]}", pior[0], 0.01)
    p = dict((n, m) for n, _, m in montagem_polia())
    confere("folga roldana-moitão", p["roldana_fixa"].min_gap(p["moitao_fixo"], 2.0), 0.2, True)
    confere("folga travessa-gancho", p["portico"].min_gap(p["moitao_fixo"], 2.0), 0.0, True)
    confere("folga alça-gancho", p["balde_carga"].min_gap(p["moitao_movel"], 2.0), 0.0, True)
    confere("folga perna-pé", p["portico"].min_gap(p["pe_1"], 2.0), 0.02, True)
    confere("folga placa-pé", p["placa_titulo"].min_gap(p["pe_1"], 2.0), 0.02, True)
    for nome, itens in (("polia", itens_mesa_polia()), ("alavanca", itens_mesa_alavanca())):
        folga = min(a.min_gap(b, 6.0) for i, a in enumerate(itens) for b in itens[i + 1:])
        confere(f"menor espaço entre peças na mesa da {nome}", folga, 2.5, True)
    mm, mr, mb = moitao().volume(), roldana().volume(), balde().volume()
    print(f"  massas (PLA maciço): moitão {mm * DENSIDADE_PLA / 1000:.1f} g + roldana "
          f"{mr * DENSIDADE_PLA / 1000:.1f} g  x  balde {mb * DENSIDADE_PLA / 1000:.1f} g")

    print("Alavanca")
    tmax = inclinacao_maxima()
    print(f"  a barra inclina até {tmax:.1f}° antes de a ponta tocar a mesa")
    base, bar = base_alavanca(), barra()
    for g in (0.0, tmax, -tmax):
        confere(f"interferência barra x base a {g:+.1f}°",
                interferencia(base, inclinar(bar, g)), 0.01)
    # fora do fio da lâmina (onde as peças se tocam de propósito) tem que sobrar folga
    sem_fio = base ^ caixa(-100, 100, -100, 100, -1, Z_PIVO - 5.0)
    for g in (0.0, tmax - 0.2, -(tmax - 0.2)):
        confere(f"folga barra-base a {g:+.1f}° (sem contar o pivô)",
                sem_fio.min_gap(inclinar(bar, g), 5.0), 1.0, True)
    for d, e, nome in ((27.0, 1.95, "R$ 1"), (25.0, 2.25, "25 c"), (23.0, 2.85, "50 c"),
                       (22.0, 1.65, "5 c"), (20.0, 2.23, "10 c")):
        pior = max(interferencia(bar, moeda_na_fenda(k, s, d, e, 0.03))
                   for k in POSICOES for s in (-1, 1))
        confere(f"moeda de {nome} nas fendas", pior, 0.01)
        confere(f"moeda de {nome}: erro no braço (mm)",
                abs(centro_moeda(5, d, e) - PASSO * 5), 0.5)
    return ok


# ====================================================================== saída

def limpar(m, vol_min=0.05):
    return uniao([p for p in m.decompose() if p.volume() > vol_min])


def para_trimesh(m):
    mesh = m.to_mesh()
    return trimesh.Trimesh(vertices=np.asarray(mesh.vert_properties)[:, :3],
                           faces=np.asarray(mesh.tri_verts), process=False)


def salvar(pasta, nome, m, resumo, qtd=1, obs=""):
    m = no_chao(limpar(m))
    os.makedirs(pasta, exist_ok=True)
    para_trimesh(m).export(os.path.join(pasta, nome))
    x0, y0, z0, x1, y1, z1 = m.bounding_box()
    v = np.asarray(m.to_mesh().vert_properties)[:, :3]
    dup = len(v) - len(np.unique(v, axis=0))
    resumo.append({"arquivo": f"{os.path.basename(pasta)}/{nome}", "quantidade": qtd,
                   "tamanho_mm": [round(x1 - x0, 1), round(y1 - y0, 1), round(z1 - z0, 1)],
                   "volume_cm3": round(m.volume() / 1000, 2), "obs": obs})
    print(f"  {nome:28s} x{qtd}  {x1 - x0:6.1f} x {y1 - y0:6.1f} x {z1 - z0:5.1f} mm"
          f"  {m.volume() / 1000:6.2f} cm³" + (f"  ! {dup} vértices coincidentes" if dup else ""))


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--verificar", action="store_true", help="só confere folgas e encaixes")
    ap.add_argument("--saida", default=os.path.join(AQUI, "stl"))
    args = ap.parse_args()
    if args.verificar:
        raise SystemExit(0 if verificar() else 1)

    resumo = []
    for sub in ("polia", "alavanca"):
        pasta = os.path.join(args.saida, sub)
        if os.path.isdir(pasta):
            for f in os.listdir(pasta):
                if f.endswith(".stl"):
                    os.remove(os.path.join(pasta, f))
    print("Polia")
    p = os.path.join(args.saida, "polia")
    salvar(p, "01_portico.stl", portico(H_PADRAO), resumo, 1, "cabe em mesa de 180 mm")
    salvar(p, "01_portico_alto.stl", portico(H_ALTO), resumo, 1,
           "opcional, no lugar do 01: mesa de 220 mm ou mais")
    salvar(p, "02_pe.stl", pe(), resumo, 2)
    salvar(p, "03_placa_titulo.stl", placa_titulo(), resumo, 1)
    salvar(p, "04_garfo_da_roldana.stl", moitao(), resumo, 2)
    salvar(p, "05_roldana.stl", roldana(), resumo, 2)
    salvar(p, "06_balde.stl", balde(), resumo, 2)
    salvar(p, "polia_mesa_completa.stl", mesa_polia(), resumo, 1, "todas as peças (com o pórtico 01)")
    print("Alavanca")
    a = os.path.join(args.saida, "alavanca")
    salvar(a, "01_base.stl", base_alavanca(), resumo, 1)
    salvar(a, "02_barra.stl", barra(), resumo, 1)
    salvar(a, "alavanca_mesa_completa.stl", mesa_alavanca(), resumo, 1, "as duas peças")
    with open(os.path.join(args.saida, "pecas.json"), "w", encoding="utf-8") as f:
        json.dump(resumo, f, ensure_ascii=False, indent=2)
    print("Verificação")
    if not verificar():
        raise SystemExit("ATENÇÃO: alguma verificação falhou")


if __name__ == "__main__":
    main()
