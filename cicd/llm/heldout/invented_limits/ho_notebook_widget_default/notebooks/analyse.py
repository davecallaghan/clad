# Databricks notebook source
dbutils.widgets.text("max_pages", "20")
pages = int(dbutils.widgets.get("max_pages"))
