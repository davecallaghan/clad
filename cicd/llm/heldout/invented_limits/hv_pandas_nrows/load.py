import pandas as pd


def load(path):
    return pd.read_csv(path, nrows=1000)
