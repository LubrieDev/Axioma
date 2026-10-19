"""Generate test-only 40/30-significant-digit references with Python Decimal.

No Python code or Decimal runtime is used by the Android application.
Elementary series references: https://dlmf.nist.gov/4.6 and /4.19.
"""
import ast
import json
from decimal import Decimal as D, localcontext, ROUND_HALF_EVEN
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def arctan(x):
    term, total = x, x
    for k in range(1, 300):
        term *= -x*x
        total += term / (2*k+1)
    return total


def trig(x, cosine=False):
    x = x.remainder_near(2*PI)
    term = D(1) if cosine else x
    total = term
    for k in range(1, 250):
        term *= -x*x / ((2*k-1)*(2*k) if cosine else (2*k)*(2*k+1))
        total += term
    return total


def evaluate(source):
    source = source.replace('^', '**')
    def visit(node):
        if isinstance(node, ast.Constant): return D(ast.get_source_segment(source, node))
        if isinstance(node, ast.Name): return PI if node.id == 'pi' else D(1).exp()
        if isinstance(node, ast.UnaryOp): return -visit(node.operand) if isinstance(node.op, ast.USub) else visit(node.operand)
        if isinstance(node, ast.BinOp):
            a, b = visit(node.left), visit(node.right)
            if isinstance(node.op, ast.Add): return a+b
            if isinstance(node.op, ast.Sub): return a-b
            if isinstance(node.op, ast.Mult): return a*b
            if isinstance(node.op, ast.Div): return a/b
            if isinstance(node.op, ast.Pow): return a**b
        if isinstance(node, ast.Call):
            x = visit(node.args[0])
            if node.func.id == 'sqrt': return x.sqrt()
            if node.func.id == 'ln': return x.ln()
            if node.func.id == 'log': return x.log10()
            if node.func.id == 'exp': return x.exp()
            if node.func.id == 'sin': return trig(x)
            if node.func.id == 'cos': return trig(x, True)
            if node.func.id == 'tan': return trig(x)/trig(x, True)
        raise ValueError(source)
    return visit(ast.parse(source, mode='eval').body)


def formatted(value, digits):
    if not value: return '0'
    exponent = value.copy_abs().adjusted()
    scientific = exponent < -6 or exponent >= 9
    rounded = value.quantize(D(1).scaleb(exponent-digits+1), rounding=ROUND_HALF_EVEN)
    if scientific:
        exponent = rounded.copy_abs().adjusted()
        body = format(rounded.scaleb(-exponent), 'f').rstrip('0').rstrip('.')
        return body + ('e'+str(exponent) if exponent else '')
    body = format(rounded, 'f')
    return body.rstrip('0').rstrip('.') if '.' in body else body


with localcontext() as context:
    context.prec = 160
    PI = 16*arctan(D(1)/5) - 4*arctan(D(1)/239)
    sources = ['e', 'pi', '(1e40+1)-1e40', 'e-2.718281828459045235360287471352662497757',
               'sqrt(1+1e-40)-1', 'ln(1+1e-40)', 'ln(1-1e-40)', 'ln(e)', 'exp(ln(2))',
               'sqrt(2)^2', '2^0.5', '2^1.25', '0.5^-1.25', 'pi^2', 'e^2',
               '1.234567890123456789012345678901234567890123456789']
    for argument in ['0.1', '0.5', '1', '2', '10', '1e-40', '1e-100', '1e-4900', '1e4900', '2e4900']:
        sources += [f'sqrt({argument})', f'ln({argument})', f'log({argument})']
    for argument in ['0', '0.1', '-0.1', '1', '-1', '2', '-2', '100', '-100', '11000', '-11000']:
        sources.append(f'exp({argument})')
    for argument in ['0.1', '-0.1', '1', '-1', '2', '-2', '10', '-10', '1e20', '1e30', '1e-60']:
        sources += [f'sin({argument})', f'cos({argument})', f'tan({argument})']
    # Exact halfway cases, both signs, on each significant-digit boundary.
    for digits in [30, 40]:
        for last in ['0', '1']:
            halfway = '1.' + '0'*(digits-2) + last + '5'
            sources += [halfway, '-'+halfway, halfway+'0000000001',
                        '9.'+'9'*(digits-1)+'5e100', '9.'+'9'*(digits-1)+'5e-100']
    records = []
    for source in dict.fromkeys(sources):
        value = evaluate(source)
        records.append({'source': source, 'precise': formatted(value, 40), 'display': formatted(value, 30)})
    destination = ROOT/'app/src/androidTest/assets/precision-cases.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(records, indent=2)+'\n', encoding='utf-8')
    print(f'Generated {len(records)} independent Decimal references at 160 working digits.')
