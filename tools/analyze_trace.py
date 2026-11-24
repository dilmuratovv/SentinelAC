#!/usr/bin/env python3
"""Summarise SentinelTrace files (-Dsentinel.trace) per realclient_probe scenario.

usage: analyze_trace.py <trace.tsv> <realclient-results.json>
"""
import json
import math
import statistics
import sys
from collections import Counter


def angle(a, b):
    d = (a - b) % 360.0
    return 360.0 - d if d > 180 else d


def step_error(delta, divisor):
    if divisor <= 0:
        return None
    q = abs(delta) / divisor
    return abs(q - round(q))


def main():
    rows = [line.rstrip('\n').split('\t') for line in open(sys.argv[1])]
    results = json.load(open(sys.argv[2]))
    for name, result in results.items():
        lo, hi = result['window_ns']
        window = [r for r in rows if lo <= int(r[0]) <= hi]
        attacks = [r for r in window if r[2] == 'atk' and len(r) >= 10]
        rots = [r for r in window if r[2] == 'rot']
        print(f'== {name}: attacks={len(attacks)} rotations={len(rots)} flagged={sorted(result["flagged"])}')
        if not attacks:
            continue
        intervals = [int(a[4]) for a in attacks if int(a[4]) < 100]
        if intervals:
            c = Counter(intervals)
            print('   attack interval ticks: mode %s share %.0f%% mean %.2f sd %.2f  top %s' % (
                c.most_common(1)[0][0], 100 * c.most_common(1)[0][1] / len(intervals), statistics.mean(intervals),
                statistics.pstdev(intervals), c.most_common(5)))
        yaw_err, pitch_err = [], []
        for a in attacks:
            ns = int(a[0])
            following = next((r for r in window if r[2] == 'rot' and int(r[0]) > ns), None)
            if following is None:
                continue
            yaw_err.append(angle(float(following[3]), float(a[6])))
            pitch_err.append(abs(float(following[4]) - float(a[7])))
        if yaw_err:
            print('   aim error at hit: yaw median %.2f p90 %.2f (<0.5: %.0f%%) | pitch median %.2f (<0.5: %.0f%%)' % (
                statistics.median(yaw_err), sorted(yaw_err)[int(len(yaw_err) * 0.9)],
                100 * sum(e < 0.5 for e in yaw_err) / len(yaw_err), statistics.median(pitch_err),
                100 * sum(e < 0.5 for e in pitch_err) / len(pitch_err)))
        combat = [r for r in rots if int(r[9]) <= 20 and (abs(float(r[5])) >= 0.5 or abs(float(r[6])) >= 0.5)]
        if combat:
            off = []
            for r in combat:
                ey, ep = step_error(float(r[5]), float(r[7])), step_error(float(r[6]), float(r[8]))
                if ey is not None and ep is not None and abs(float(r[6])) >= 0.5 and abs(float(r[5])) >= 1:
                    off.append(ey > 0.2 and ep > 0.2)
            print('   combat rotations %d, sensitivity step yaw %s pitch %s, off-step both axes %s' % (
                len(combat), combat[-1][7], combat[-1][8],
                f'{100 * sum(off) / len(off):.0f}% of {len(off)}' if off else 'n/a'))
            dy = [abs(float(r[5])) for r in combat]
            print('   |dyaw| median %.2f max %.1f; zero-pitch-change share %.0f%%' % (
                statistics.median(dy), max(dy), 100 * sum(abs(float(r[6])) < 1e-4 for r in combat) / len(combat)))


if __name__ == '__main__':
    main()
